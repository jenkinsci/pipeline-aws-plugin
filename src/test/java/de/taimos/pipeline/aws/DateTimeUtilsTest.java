package de.taimos.pipeline.aws;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.Test;
import org.junit.Assert;

public class DateTimeUtilsTest {

	public void verifyParse(String parse, ZonedDateTime dt) throws Exception {
		ZoneId tz = ZoneOffset.UTC;
		Assert.assertEquals(DateTimeUtils.parse(parse).withZoneSameLocal(tz), dt.withZoneSameLocal(tz));
	}

	@Test
	public void parseDate() throws Exception {
		ZonedDateTime now = ZonedDateTime.now();
		verifyParse(now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME), now);
		verifyParse("2018-02-05T11:15:12Z", ZonedDateTime.of(
					LocalDate.of(2018, 2, 5),
					LocalTime.of(11, 15, 12),
					ZoneOffset.UTC
		));
		verifyParse("2020-02-21T23:25:31.593+0000", ZonedDateTime.of(
					LocalDate.of(2020, 2, 21),
					LocalTime.of(23, 25, 31, 593000000),
					ZoneOffset.UTC
		));
		verifyParse("2016-02-14T21:32:04.150+04:00", ZonedDateTime.of(
					LocalDate.of(2016, 2, 14),
					LocalTime.of(21, 32, 4, 150000000),
					ZoneOffset.ofHours(4)
		));
	}


}
