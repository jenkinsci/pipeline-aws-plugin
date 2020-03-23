package de.taimos.pipeline.aws;

import java.time.ZonedDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoField;

public final class DateTimeUtils {
	private static final DateTimeFormatter DATE_TIME_NANOSECONDS_OFFSET_FORMATTER = 
		new DateTimeFormatterBuilder()
		.parseCaseInsensitive()
		.append(ISO_LOCAL_DATE_TIME)
		.appendFraction(ChronoField.NANO_OF_SECOND, 0, 3, true)
		.appendOffset("+HHmm", "Z")
		.toFormatter();

	public static ZonedDateTime parse(String date) {
		try {
			return ZonedDateTime.parse(date, DateTimeFormatter.ISO_ZONED_DATE_TIME);
		} catch (DateTimeParseException e) {
			return ZonedDateTime.parse(date, DATE_TIME_NANOSECONDS_OFFSET_FORMATTER);
		}
	}
}
