package com.user.management.repository.projection;

import com.user.management.entity.Gender;
import com.user.management.entity.Locality;

/**
 * One row of a count grouped by locality and gender.
 *
 * <p>The office reads the register as four numbers - local men, local women, and the
 * same two for the sewadars who travel in - so the database groups it once rather
 * than being asked four times. Either field is null where the record has none, and
 * those rows are reported under their own heading instead of being folded into a
 * bucket they do not belong to.</p>
 */
public interface LocalityGenderCount {

    Locality getLocality();

    Gender getGender();

    long getCount();
}
