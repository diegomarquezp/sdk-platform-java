/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud;

import com.google.api.core.BetaApi;
import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Objects;

/** Represents a Date without time, such as 2017-03-17. Date is timezone independent. */
@BetaApi("This is going to be replaced with LocalDate from threetenbp")
public final class Date implements Comparable<Date>, Serializable {

  // Date format "yyyy-mm-dd"
  private static final long serialVersionUID = 8067099123096783929L;
  private final int year;
  private final int month;
  private final int dayOfMonth;

  private Date(int year, int month, int dayOfMonth) {
    Preconditions.checkArgument(year > 0, "Invalid year: " + year);
    Preconditions.checkArgument(month > 0 && month <= 12, "Invalid month: " + month);
    Preconditions.checkArgument(dayOfMonth > 0 && dayOfMonth <= 31, "Invalid day: " + dayOfMonth);
    this.year = year;
    this.month = month;
    this.dayOfMonth = dayOfMonth;
  }

  /**
   * Constructs a new Date instance.
   *
   * @param year must be greater than 0
   * @param month must be between [1,12]
   * @param dayOfMonth must be between [1,31]
   */
  public static Date fromYearMonthDay(int year, int month, int dayOfMonth) {
    return new Date(year, month, dayOfMonth);
  }

  /**
   * @param date Data in RFC 3339 date format (yyyy-mm-dd).
   */
  public static Date parseDate(String date) {
    Preconditions.checkNotNull(date);
    final String invalidDate = "Invalid date: " + date;
    Preconditions.checkArgument(date.length() == 10, invalidDate);
    Preconditions.checkArgument(date.charAt(4) == '-', invalidDate);
    Preconditions.checkArgument(date.charAt(7) == '-', invalidDate);
    try {
      int year = Integer.parseInt(date.substring(0, 4));
      int month = Integer.parseInt(date.substring(5, 7));
      int dayOfMonth = Integer.parseInt(date.substring(8, 10));
      return new Date(year, month, dayOfMonth);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(invalidDate, e);
    }
  }

  /**
   * Convert a Google Date to a Java Util Date.
   *
   * @param date the date of the Google Date.
   * @return java.util.Date
   */
  public static java.util.Date toJavaUtilDate(Date date) {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    // Calender.MONTH starts from 0 while G C date starts from 1
    cal.set(date.year, date.month - 1, date.dayOfMonth);
    return cal.getTime();
  }

  /**
   * Convert a Java Util Date to a Google Date.
   *
   * @param date the date of the java.util.Date
   * @return Google Java Date
   */
  public static Date fromJavaUtilDate(java.util.Date date) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    // Calender.MONTH starts from 0 while G C date starts from 1
    return new Date(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
  }

  /** Returns the year. */
  public int getYear() {
    return year;
  }

  /** Returns the month between 1 and 12 inclusive. */
  public int getMonth() {
    return month;
  }

  /** Returns day of month between 1 and 31 inclusive. */
  public int getDayOfMonth() {
    return dayOfMonth;
  }

  @Override
  public String toString() {
    return String.format("%04d-%02d-%02d", year, month, dayOfMonth);
  }

  StringBuilder toString(StringBuilder b) {
    return b.append(toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Date that = (Date) o;
    return year == that.year && month == that.month && dayOfMonth == that.dayOfMonth;
  }

  @Override
  public int hashCode() {
    return Objects.hash(year, month, dayOfMonth);
  }

  @Override
  public int compareTo(Date other) {
    int r = Integer.compare(year, other.year);
    if (r == 0) {
      r = Integer.compare(month, other.month);
      if (r == 0) {
        r = Integer.compare(dayOfMonth, other.dayOfMonth);
      }
    }
    return r;
  }
}
