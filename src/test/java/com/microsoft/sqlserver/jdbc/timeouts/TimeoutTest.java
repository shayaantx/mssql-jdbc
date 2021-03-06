/*
 * Microsoft JDBC Driver for SQL Server Copyright(c) Microsoft Corporation All rights reserved. This program is made
 * available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */

package com.microsoft.sqlserver.jdbc.timeouts;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Set;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import com.microsoft.sqlserver.testframework.AbstractTest;


@RunWith(JUnitPlatform.class)
public class TimeoutTest extends AbstractTest {
    private static final String SQL_SERVER_TIMEOUT_THREAD = "com.microsoft.sqlserver.jdbc.SQLServerTimeoutManager";
    private static final String SQL_SERVER_TIMEOUT_TASK_THREAD = "com.microsoft.sqlserver.jdbc.SQLServerTimeoutManager.TimeoutTaskWorker";

    @BeforeAll
    public static void setup() throws Exception {
        AbstractTest.setup();
        if (connection != null) {
            // we don't need this internal connection for the timeout tests
            connection.close();
        }
    }

    @Test
    public void testBasicQueryTimeout() {
        boolean exceptionThrown = false;
        try {
            // wait 1 minute and timeout after 10 seconds
            Assert.assertTrue("Select succeeded", runQuery("WAITFOR DELAY '00:01'", 10));
        } catch (SQLException e) {
            exceptionThrown = true;
            Assert.assertTrue("Timeout exception not thrown", e.getClass().equals(SQLTimeoutException.class));
        }
        Assert.assertTrue("A SQLTimeoutException was expected", exceptionThrown);
    }

    @Test
    public void testQueryTimeoutValid() {
        boolean exceptionThrown = false;
        int timeoutInSeconds = 10;
        long start = System.currentTimeMillis();
        try {
            // wait 1 minute and timeout after 10 seconds
            Assert.assertTrue("Select succeeded", runQuery("WAITFOR DELAY '00:01'", timeoutInSeconds));
        } catch (SQLException e) {
            int secondsElapsed = (int) ((System.currentTimeMillis() - start) / 1000);
            Assert.assertTrue("Query did not timeout expected, elapsedTime=" + secondsElapsed,
                    secondsElapsed >= timeoutInSeconds);
            exceptionThrown = true;
            Assert.assertTrue("Timeout exception not thrown", e.getClass().equals(SQLTimeoutException.class));
        }
        Assert.assertTrue("A SQLTimeoutException was expected", exceptionThrown);
    }

    @Test
    public void testSqlTimeoutThreadsStopAfterConnectionCloses() throws InterruptedException {
        testQueryTimeoutValid();
        // wait 5 seconds if the cpu is taking longer than normal to stop the timeout threads
        Thread.sleep(5000);
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_THREAD));
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_TASK_THREAD));
    }

    @Test
    public void testSqlTimeoutThreadsRestartAfterNewConnectionsAreMade() throws InterruptedException {
        testQueryTimeoutValid();

        // wait 5 seconds if the cpu is taking longer than normal to stop the timeout threads
        Thread.sleep(5000);
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_THREAD));
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_TASK_THREAD));

        testQueryTimeoutValid();

        // wait 5 seconds if the cpu is taking longer than normal to stop the timeout threads
        Thread.sleep(5000);
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_THREAD));
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_TASK_THREAD));
    }

    @Test
    public void testTimeoutThreadsStillRunningDuringMultipleStatements() throws InterruptedException, SQLException {
        try (Connection con = DriverManager.getConnection(connectionString)) {
            boolean exceptionThrown = false;
            try (PreparedStatement preparedStatement = con.prepareStatement("WAITFOR DELAY '00:01'")) {
                preparedStatement.setQueryTimeout(10);
                preparedStatement.execute();
            } catch (SQLException e) {
                Assert.assertTrue("Timeout exception not thrown", e.getClass().equals(SQLTimeoutException.class));
                exceptionThrown = true;
            }
            Assert.assertTrue("A SQLTimeoutException was expected", exceptionThrown);
            Assert.assertTrue(isThreadStillRunning(SQL_SERVER_TIMEOUT_THREAD));
            Assert.assertTrue(isThreadStillRunning(SQL_SERVER_TIMEOUT_TASK_THREAD));

            exceptionThrown = false;
            try (PreparedStatement preparedStatement = con.prepareStatement("WAITFOR DELAY '00:01'")) {
                preparedStatement.setQueryTimeout(10);
                preparedStatement.execute();
            } catch (SQLException e) {
                Assert.assertTrue("Timeout exception not thrown", e.getClass().equals(SQLTimeoutException.class));
                exceptionThrown = true;
            }
            Assert.assertTrue("A SQLTimeoutException was expected", exceptionThrown);
            Assert.assertTrue(isThreadStillRunning(SQL_SERVER_TIMEOUT_THREAD));
            Assert.assertTrue(isThreadStillRunning(SQL_SERVER_TIMEOUT_TASK_THREAD));
        }
        // wait 5 seconds if the cpu is taking longer than normal to stop the timeout threads
        Thread.sleep(5000);
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_THREAD));
        Assert.assertFalse(isThreadStillRunning(SQL_SERVER_TIMEOUT_TASK_THREAD));
    }

    private boolean isThreadStillRunning(String threadName) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread thread : threadSet) {
            if (thread.getName().equalsIgnoreCase(threadName)) {
                return true;
            }
        }
        return false;
    }

    private boolean runQuery(String query, int timeout) throws SQLException {
        try (Connection con = DriverManager.getConnection(connectionString);
                PreparedStatement preparedStatement = con.prepareStatement(query)) {
            // set provided timeout
            preparedStatement.setQueryTimeout(timeout);
            return preparedStatement.execute();
        }
    }
}
