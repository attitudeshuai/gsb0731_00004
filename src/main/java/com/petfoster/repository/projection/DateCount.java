package com.petfoster.repository.projection;

import java.time.LocalDate;

public interface DateCount {
    LocalDate getDate();
    Long getCount();
}
