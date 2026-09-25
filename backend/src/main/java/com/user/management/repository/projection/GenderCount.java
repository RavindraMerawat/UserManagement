package com.user.management.repository.projection;

import com.user.management.entity.Gender;

/**
 * One row of a count grouped by gender.
 *
 * <p>{@code gender} is null for sewadars whose record has none, which is a real
 * case in the register and is why the dashboard reads male and female by name
 * rather than assuming two buckets that add up to the total.</p>
 */
public interface GenderCount {

    Gender getGender();

    long getCount();
}
