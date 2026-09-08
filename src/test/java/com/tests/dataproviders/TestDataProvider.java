package com.tests.dataproviders;

import com.framework.utils.JsonUtils;
import com.tests.models.User;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.testng.annotations.DataProvider;

public class TestDataProvider {

    @DataProvider(name = "usersFromJson")
    public static Object[][] usersFromJson() {
        List<User> users = JsonUtils.fromJsonArrayClasspath("testdata/users.json", User.class);
        Object[][] data = new Object[users.size()][1];
        for (int i = 0; i < users.size(); i++) {
            data[i][0] = users.get(i);
        }
        return data;
    }

    @DataProvider(name = "loginCredentialsFromCsv")
    public static Object[][] loginCredentialsFromCsv() throws IOException {
        try (Reader reader = new InputStreamReader(
                TestDataProvider.class.getClassLoader().getResourceAsStream("testdata/login_credentials.csv"),
                StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            List<CSVRecord> records = parser.getRecords();
            Object[][] data = new Object[records.size()][3];
            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                data[i][0] = record.get("username");
                data[i][1] = record.get("password");
                data[i][2] = record.get("expectedResult");
            }
            return data;
        }
    }
}
