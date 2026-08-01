package com.petfoster.common;

import java.time.LocalDate;

public interface DailyCountProjection {
    LocalDate getDate();
    Long getCount();
}
