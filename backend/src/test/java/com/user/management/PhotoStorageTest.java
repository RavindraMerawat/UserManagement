package com.user.management;

import com.user.management.entity.Photo;
import com.user.management.entity.PhotoOwnerType;
import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.repository.PhotoRepository;
import com.user.management.repository.UserRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.PhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Storing and replacing a photo.
 *
 * <p>The case that matters is <b>replacing</b> one: a second upload for the same owner
 * has to update the row in place. An insert would collide with the
 * {@code (owner_type, owner_id)} unique key and surface as a save failure.</p>
 *
 * <p>The images here are a few hundred kB rather than a token handful of bytes,
 * because the bug this test guards was a column that only accepted 255 - and a tiny
 * fixture would have fitted it and proved nothing.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PhotoStorageTest {

    @Autowired PhotoService photoService;
    @Autowired PhotoRepository photoRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void signIn() {
        User user = userRepository.save(User.builder()
                .username("photo-admin-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Photo Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(user, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("A real-sized photo round-trips byte for byte")
    void storesAPhotoOfARealisticSize() {
        byte[] image = png(400, 400);
        assertThat(image.length).isGreaterThan(100_000);

        photoService.store(PhotoOwnerType.SEWADAR, 1L, upload(image, "image/png"));

        Photo stored = photoService.get(PhotoOwnerType.SEWADAR, 1L);
        assertThat(stored.getData()).isEqualTo(image);
        assertThat(stored.getSizeBytes()).isEqualTo(image.length);
        assertThat(stored.getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Uploading again replaces the image rather than adding a second row")
    void replacingUpdatesInPlace() {
        byte[] first = png(300, 300);
        byte[] second = png(420, 420);
        assertThat(second).isNotEqualTo(first);

        photoService.store(PhotoOwnerType.SEWADAR, 1L, upload(first, "image/png"));
        Long idAfterFirst = photoService.get(PhotoOwnerType.SEWADAR, 1L).getId();

        photoService.store(PhotoOwnerType.SEWADAR, 1L, upload(second, "image/png"));

        Photo stored = photoService.get(PhotoOwnerType.SEWADAR, 1L);
        assertThat(stored.getId()).isEqualTo(idAfterFirst);
        assertThat(stored.getData()).isEqualTo(second);
        assertThat(stored.getSizeBytes()).isEqualTo(second.length);
        assertThat(photoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("A sewadar and a user with the same id each keep their own photo")
    void ownerTypeIsPartOfTheKey() {
        byte[] sewadarImage = png(300, 300);
        byte[] userImage = png(360, 360);

        photoService.store(PhotoOwnerType.SEWADAR, 1L, upload(sewadarImage, "image/png"));
        photoService.store(PhotoOwnerType.USER, 1L, upload(userImage, "image/png"));

        assertThat(photoService.get(PhotoOwnerType.SEWADAR, 1L).getData()).isEqualTo(sewadarImage);
        assertThat(photoService.get(PhotoOwnerType.USER, 1L).getData()).isEqualTo(userImage);
        assertThat(photoRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deleting leaves the other owner's photo alone")
    void deleteOnlyRemovesOneOwner() {
        photoService.store(PhotoOwnerType.SEWADAR, 1L, upload(png(300, 300), "image/png"));
        photoService.store(PhotoOwnerType.USER, 1L, upload(png(300, 300), "image/png"));

        photoService.delete(PhotoOwnerType.SEWADAR, 1L);

        assertThatThrownBy(() -> photoService.get(PhotoOwnerType.SEWADAR, 1L))
                .hasMessageContaining("No photo on file");
        assertThat(photoRepository.existsByOwnerTypeAndOwnerId(PhotoOwnerType.USER, 1L)).isTrue();
    }

    @Test
    @DisplayName("The size limit the service enforces is the size the column is built for")
    void serviceLimitMatchesTheColumn() {
        // Two constants that disagree would mean an upload the service accepts and the
        // database rejects - which is exactly the failure this pair was introduced for.
        byte[] tooBig = new byte[Photo.MAX_DATA_BYTES + 1];
        assertThatThrownBy(() -> photoService.store(
                PhotoOwnerType.SEWADAR, 1L, upload(tooBig, "image/png")))
                .hasMessageContaining("3 MB or smaller");
    }

    @Test
    @DisplayName("A renamed non-image is refused")
    void refusesAFileThatIsNotAnImage() {
        byte[] text = "this is not an image at all, just text pretending".getBytes();
        assertThatThrownBy(() -> photoService.store(
                PhotoOwnerType.SEWADAR, 1L, upload(text, "image/png")))
                .hasMessageContaining("not a valid PNG image");
    }

    @Test
    @DisplayName("A failed replacement leaves the stored photo as it was")
    void aRefusedUploadDoesNotDamageTheStoredPhoto() {
        byte[] good = png(300, 300);
        photoService.store(PhotoOwnerType.SEWADAR, 1L, upload(good, "image/png"));

        assertThatThrownBy(() -> photoService.store(PhotoOwnerType.SEWADAR, 1L,
                upload("not an image".getBytes(), "image/png")))
                .hasMessageContaining("not a valid PNG");

        assertThat(photoService.get(PhotoOwnerType.SEWADAR, 1L).getData()).isEqualTo(good);
    }

    @Test
    @DisplayName("Deleting the owner takes its photo with it")
    void deletingAnOwnerRemovesThePhoto() {
        /*
         * Photos are keyed by owner with no foreign key to cascade, so nothing in the
         * database removes them when the owner goes. Before this was fixed, deleting
         * an account left its image behind for ever - and the next account handed
         * that id would have inherited someone else's face.
         */
        photoService.store(PhotoOwnerType.USER, 4242L, upload(png(300, 300), "image/png"));
        assertThat(photoRepository.existsByOwnerTypeAndOwnerId(PhotoOwnerType.USER, 4242L)).isTrue();

        photoService.delete(PhotoOwnerType.USER, 4242L);

        assertThat(photoRepository.existsByOwnerTypeAndOwnerId(PhotoOwnerType.USER, 4242L)).isFalse();
        assertThat(photoRepository.count()).isZero();
    }

    @Test
    @DisplayName("Removing a photo for an owner that has none is not an error")
    void deletingAnAbsentPhotoIsHarmless() {
        // The delete runs on every owner removal, including the many that never had
        // a photo, so it has to be a no-op rather than a failure.
        photoService.delete(PhotoOwnerType.SEWADAR, 9999L);
        assertThat(photoRepository.count()).isZero();
    }

    // ---- helpers ----

    private MockMultipartFile upload(byte[] bytes, String contentType) {
        return new MockMultipartFile("file", "photo.png", contentType, bytes);
    }

    /**
     * A real, uncompressed PNG of the given size - big enough that a column sized for
     * a handful of bytes cannot hold it, and valid enough to pass the magic-byte check.
     */
    private byte[] png(int width, int height) {
        byte[] raw = new byte[height * (1 + width * 3)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0; // filter: none
            for (int x = 0; x < width; x++) {
                raw[at++] = (byte) (x % 256);
                raw[at++] = (byte) (y % 256);
                raw[at++] = (byte) ((x + y) % 256);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.writeBytes(new byte[]{8, 2, 0, 0, 0}); // 8 bit, truecolour
        writeChunk(out, "IHDR", ihdr.toByteArray());

        writeChunk(out, "IDAT", deflate(raw));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private byte[] deflate(byte[] raw) {
        // Level 0: no compression, so the fixture's size stays proportional to the image.
        Deflater deflater = new Deflater(Deflater.NO_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes();
        out.writeBytes(typeBytes);
        out.writeBytes(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }
}
