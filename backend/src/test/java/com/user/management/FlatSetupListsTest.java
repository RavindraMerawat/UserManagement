package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.model.AreaRequest;
import com.user.management.model.AreaResponse;
import com.user.management.model.SatsangPointRequest;
import com.user.management.model.SatsangPointResponse;
import com.user.management.repository.AreaRepository;
import com.user.management.repository.SatsangPointRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Areas and satsang points are lists of their own.
 *
 * <p>Each used to belong to the one above it, which is what made the Add Sewadar
 * pickers narrow one by the other. These pin the three things that follow from
 * flattening them: a name is made without naming a parent, it is unique across the
 * whole list rather than within a parent, and everyone sees every one of them
 * whatever zones they can reach.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlatSetupListsTest {

    @Autowired SetupService setupService;
    @Autowired AreaRepository areaRepository;
    @Autowired SatsangPointRepository pointRepository;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone north;
    private Zone south;

    @BeforeEach
    void seed() {
        north = zoneRepository.save(Zone.builder().code("FN").name("Flat North").active(true).build());
        south = zoneRepository.save(Zone.builder().code("FS").name("Flat South").active(true).build());
        signInAsAdmin();
    }

    @Test
    @DisplayName("an area is made from a name alone - no zone to put it under")
    void anAreaNeedsOnlyItsName() {
        AreaResponse area = setupService.createArea(new AreaRequest("Indore", true));

        assertThat(area.name()).isEqualTo("Indore");
        assertThat(area.active()).isTrue();
        assertThat(setupService.listAreas(null)).extracting(AreaResponse::name)
                .containsExactly("Indore");
    }

    @Test
    @DisplayName("a satsang point is made from a name alone - no area to put it in")
    void aPointNeedsOnlyItsName() {
        SatsangPointResponse point = setupService.createPoint(
                new SatsangPointRequest("Nanda Nagar", true));

        assertThat(point.name()).isEqualTo("Nanda Nagar");
        assertThat(setupService.listPoints(null)).extracting(SatsangPointResponse::name)
                .containsExactly("Nanda Nagar");
    }

    @Test
    @DisplayName("the name is unique across the whole list, not once per zone")
    void oneAreaPerName() {
        setupService.createArea(new AreaRequest("Indore", true));

        assertThatThrownBy(() -> setupService.createArea(new AreaRequest("indore", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already an area called");
    }

    @Test
    @DisplayName("the same goes for a satsang point")
    void onePointPerName() {
        setupService.createPoint(new SatsangPointRequest("Nanda Nagar", true));

        assertThatThrownBy(() -> setupService.createPoint(
                new SatsangPointRequest("NANDA NAGAR", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already a satsang point called");
    }

    @Test
    @DisplayName("the lists come back whole - no zone narrows the areas, no area the points")
    void nothingNarrowsAnything() {
        setupService.createArea(new AreaRequest("Indore", true));
        setupService.createArea(new AreaRequest("Dewas", true));
        setupService.createPoint(new SatsangPointRequest("Nanda Nagar", true));
        setupService.createPoint(new SatsangPointRequest("Sudama Nagar", true));

        /*
         * Sewadars in two different zones, because this is what the old shape would
         * have split: the Add Sewadar form reads these same two lists and used to
         * show only the areas under the chosen zone, and only the points under the
         * chosen area.
         */
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber("FS-N").name("In The North").zone(north).area("Indore").build());
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber("FS-S").name("In The South").zone(south).area("Dewas").build());

        assertThat(setupService.listAreas(null)).extracting(AreaResponse::name)
                .containsExactlyInAnyOrder("Dewas", "Indore");
        assertThat(setupService.listPoints(null)).extracting(SatsangPointResponse::name)
                .containsExactlyInAnyOrder("Nanda Nagar", "Sudama Nagar");
    }

    @Test
    @DisplayName("an area a sewadar still carries is retired, not deleted out from under them")
    void anAreaInUseIsRetired() {
        AreaResponse area = setupService.createArea(new AreaRequest("Indore", true));
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber("FS-1").name("In Indore").zone(north).area("Indore").build());

        setupService.deleteArea(area.id());

        assertThat(areaRepository.findById(area.id())).get()
                .satisfies(a -> assertThat(a.isActive()).isFalse());
    }

    @Test
    @DisplayName("an area nobody is in is removed outright")
    void anUnusedAreaGoes() {
        AreaResponse area = setupService.createArea(new AreaRequest("Nowhere", true));

        setupService.deleteArea(area.id());

        assertThat(areaRepository.findById(area.id())).isEmpty();
    }

    @Test
    @DisplayName("a satsang point a sewadar still carries is retired too")
    void aPointInUseIsRetired() {
        SatsangPointResponse point = setupService.createPoint(
                new SatsangPointRequest("Nanda Nagar", true));
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber("FS-2").name("At The Point").zone(south)
                .centerPoint("Nanda Nagar").build());

        setupService.deletePoint(point.id());

        assertThat(pointRepository.findById(point.id())).get()
                .satisfies(p -> assertThat(p.isActive()).isFalse());
    }

    @Test
    @DisplayName("listing only the live ones leaves the retired ones out")
    void activeOnly() {
        AreaResponse kept = setupService.createArea(new AreaRequest("Indore", true));
        setupService.createArea(new AreaRequest("Retired", false));

        assertThat(setupService.listAreas(true)).extracting(AreaResponse::id)
                .containsExactly(kept.id());
    }

    // ------------------------------------------------------------------ helpers

    private void signInAsAdmin() {
        signIn(User.builder()
                .username("flat-admin-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Flat Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build(), null);
    }

    private void signIn(User user, String designation) {
        User saved = userRepository.save(user);
        AppUserPrincipal principal = new AppUserPrincipal(saved, null, designation);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
