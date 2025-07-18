/*
 * © Copyright Serdar Basegmez. 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.openntf.xlogback.config;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.html.HTMLLayout;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.ModelUtil;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import ch.qos.logback.core.util.FileSize;
import java.util.List;
import org.openntf.xlogback.console.DominoConsoleAppender;
import org.openntf.xlogback.openlog.OpenLogAppender;
import org.openntf.xlogback.utils.LogUtils;
import org.openntf.xlogback.utils.StrUtils;
import org.openntf.xlogback.utils.Utils;
import org.slf4j.LoggerFactory;

public class AutoConfig {

    private static final String APPENDER_NAME_CONSOLE = "console";
    private static final String APPENDER_NAME_OPENLOG = "openlog";
    private static final String APPENDER_NAME_ROLLINGFILE = "rollingfile";

    private static AutoConfig instance;

    private final LoggerContext lc;
    private final StatusUtil statusUtil;

    private AutoConfig(LoggerContext lc) {
        this.lc = lc;
        this.statusUtil = new StatusUtil(lc);
    }

    /**
     * This is the entry point for autoconfiguration.
     */
    public static void init() {
        if (instance != null) {
            // Already initialized.
            instance.addWarn("Auto Configuration already initialized.");
            return;
        }

        int enabled = LogSettings.getIntegerValue(LogSettings.SETTING_AUTO, 0);

        if (enabled != 1) {
            return;
        }

        // This will inevitably start the internal autoconfiguration.
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        try {
            instance = new AutoConfig(lc);
            instance.doInit();
        } catch (Throwable t) {
            // Acting paranoid...
            instance.addError("Auto Configuration failed with an error!", t);
        }

    }

    /**
     * We reset configuration here. We need to get the Debug listener back online because it will be deleted after context reset.
     */
    private void resetConfiguration() {
        lc.reset();

        // Debug flag will be effective only if auto configuration is enabled.
        // Configuration files already support 'debug="true"' flag.
        if (LogSettings.inDebugMode()) {
            // StatusListener mechanism is managed inside Logback.
            // We make sure to stop LoggerContext at the plugin stop.
            lc.getStatusManager().add(new OnConsoleStatusListener());
        }
    }

    /**
     * Internal initialization for the auto configuration.
     * <p>
     * Logback would have been configured proviously. So we will backup the safe configuration and try to fall back properly.
     */
    private void doInit() {
        JoranConfigurator joran = new JoranConfigurator();
        boolean success = false;

        joran.setContext(lc);
        Model lastGoodConfig = joran.recallSafeConfiguration();

        addInfo("Auto Configuration enabled.");
        try {
            // autoconfig
            resetConfiguration();
            success = configure();
        } catch (Throwable t) {
            addError("Auto Configuration failed with an error!", t);
        }

        if (success) {
            addInfo("XLogback switched to the Automatic Configuration");
        } else {
            try {
                if (lastGoodConfig != null) {
                    resetConfiguration();
                    ModelUtil.resetForReuse(lastGoodConfig);
                    joran.processModel(lastGoodConfig);
                    addWarn("Auto Configuration returned to the last good configuration.");
                }
            } catch (Throwable t) {
                addError("Auto Configuration unable to return to the last good configuration.", t);
                new BasicConfigurator().configure(lc);
                addWarn("Basic Configuration applied.");
            }
        }

        // We already print everything in debug mode
        if (!LogSettings.inDebugMode()) {
            printProblems();
        }

        // Do we really need to clear this?
        lc.getStatusManager().clear();
    }

    /**
     * Prepare and add appenders. If any appender wrote a status message with error code, we will assume that auto configuration
     * failed.
     * <p>
     * This is not an ideal decision and should be revisited in the future.
     *
     * @return true if everything works well.
     */
    private boolean configure() {
        DominoConsoleAppender<ILoggingEvent> consoleAppender = getConsoleAppender();
        OpenLogAppender openLogAppender = getOpenLogAppender();
        RollingFileAppender<ILoggingEvent> rollingFileAppender = getRollingFileAppender();

        Logger root = lc.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(consoleAppender);
        root.addAppender(openLogAppender);
        root.addAppender(rollingFileAppender);

        // Set the root level to the minimum of three appenders.
        root.setLevel(findRootLevel());

        // This is somewhat important for XPages apps.
        // Getting packaging data for stack traces needs classLoader access.
        // Logback does not wrap that part with relevant code block so it throws exception.
        lc.setPackagingDataEnabled(false);

        // Check status levels for any ERROR from configurators
        int highestLevel = statusUtil.getHighestLevel(0);
        return (highestLevel != ErrorStatus.ERROR);
    }

    private DominoConsoleAppender<ILoggingEvent> getConsoleAppender() {
        DominoConsoleAppender<ILoggingEvent> appender = new DominoConsoleAppender<>();

        Level logLevel = LogSettings.getLogLevelValue(LogSettings.SETTING_CONSOLE_LOGLEVEL);
        String patternStr = LogSettings.getStringValue(LogSettings.SETTING_CONSOLE_PATTERN);

        appender.setContext(lc);
        appender.setName(APPENDER_NAME_CONSOLE);

        PatternLayout layout = new PatternLayout();
        layout.setPattern(patternStr);
        layout.setContext(lc);

        LogUtils.injectCustomConverters(layout);

        appender.setLayout(layout);

        if (logLevel != null) {
            addInfo("Console Log Level is " + logLevel);

            ThresholdFilter filter = new ThresholdFilter();
            filter.setLevel(logLevel.levelStr);
            appender.addFilter(filter);
            filter.start();
        }

        layout.start();
        appender.start();

        return appender;
    }

    private OpenLogAppender getOpenLogAppender() {
        OpenLogAppender appender = new OpenLogAppender();

        String dbServer = LogSettings.getStringValue(LogSettings.SETTING_OPENLOG_DBSERVER);
        String dbPath = LogSettings.getStringValue(LogSettings.SETTING_OPENLOG_DBPATH);
        int suppressEventStack = LogSettings.getIntegerValue(LogSettings.SETTING_OPENLOG_SUPPRESSEVENTSTACK, 0);
        int expireDays = LogSettings.getIntegerValue(LogSettings.SETTING_OPENLOG_EXPIREDAYS, 0);
        int debugLevel = LogSettings.getIntegerValue(LogSettings.SETTING_OPENLOG_DEBUGLEVEL, 0);
        Level logLevel = LogSettings.getLogLevelValue(LogSettings.SETTING_OPENLOG_LOGLEVEL);
        String defaultApp = LogSettings.getStringValue(LogSettings.SETTING_OPENLOG_DEFAULTAPP);
        String defaultAgent = LogSettings.getStringValue(LogSettings.SETTING_OPENLOG_DEFAULTAGENT);

        appender.setContext(lc);
        appender.setName(APPENDER_NAME_OPENLOG);
        appender.setTargetDbServer(dbServer);
        appender.setTargetDbPath(dbPath);
        appender.setSuppressEventStack(suppressEventStack == 1);
        appender.setDebugLevel(debugLevel);
        appender.setLogExpireDays(expireDays);
        appender.setDefaultAgent(defaultAgent);
        appender.setDefaultApp(defaultApp);

        if (logLevel != null) {
            addInfo("OpenLog Log Level is " + logLevel);

            ThresholdFilter filter = new ThresholdFilter();
            filter.setLevel(logLevel.levelStr);
            appender.addFilter(filter);
            filter.start();
        }

        appender.start();

        return appender;
    }

    private RollingFileAppender<ILoggingEvent> getRollingFileAppender() {
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();

        String logFilePath = LogSettings.getStringValue(LogSettings.SETTING_FILE_PATH);
        String logFileName;
        String logFilePattern;

        if (StrUtils.isEmpty(logFilePath)) {
            logFilePath = LogSettings.getLogbackLoggingPath();
        }

        logFileName = Utils.toSafeFolder(logFilePath) + LogUtils.getPlatformName() + ".html";
        logFilePattern = Utils.toSafeFolder(logFilePath) + LogUtils.getPlatformName() + ".%i.html";

        String pattern = LogSettings.getStringValue(LogSettings.SETTING_FILE_PATTERN);
        String maxFileSize = LogSettings.getStringValue(LogSettings.SETTING_FILE_MAXSIZE);
        int maxIndex = LogSettings.getIntegerValue(LogSettings.SETTING_FILE_MAXINDEX, 20);
        Level logLevel = LogSettings.getLogLevelValue(LogSettings.SETTING_FILE_LOGLEVEL);

        appender.setContext(lc);
        appender.setFile(logFileName);
        appender.setName(APPENDER_NAME_ROLLINGFILE);

        FixedWindowRollingPolicy rollPolicy = new FixedWindowRollingPolicy();
        rollPolicy.setContext(lc);
        rollPolicy.setFileNamePattern(logFilePattern);
        rollPolicy.setMinIndex(1);
        rollPolicy.setMaxIndex(maxIndex);
        rollPolicy.setParent(appender);
        rollPolicy.start();

        SizeBasedTriggeringPolicy<ILoggingEvent> triggerPolicy = new SizeBasedTriggeringPolicy<>();
        triggerPolicy.setContext(lc);

        // TODO Error checking for maxFileSize
        triggerPolicy.setMaxFileSize(FileSize.valueOf(maxFileSize));

        triggerPolicy.start();

        HTMLLayout layout = new HTMLLayout();
        layout.setContext(lc);
        layout.setPattern(pattern);

        LogUtils.injectCustomThrowableRenderer(layout);

        layout.start();

        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(lc);
        encoder.setLayout(layout);
        encoder.start();

        if (logLevel != null) {
            addInfo("RollingFile Log Level is " + logLevel);

            ThresholdFilter filter = new ThresholdFilter();
            filter.setLevel(logLevel.levelStr);
            appender.addFilter(filter);
            filter.start();
        }

        appender.setRollingPolicy(rollPolicy);
        appender.setTriggeringPolicy(triggerPolicy);
        appender.setEncoder(encoder);
        appender.start();

        return appender;
    }

    private Level findRootLevel() {
        int cl = LogSettings.getLogLevelValue(LogSettings.SETTING_CONSOLE_LOGLEVEL, Level.OFF).levelInt;
        int fl = LogSettings.getLogLevelValue(LogSettings.SETTING_FILE_LOGLEVEL, Level.OFF).levelInt;
        int ol = LogSettings.getLogLevelValue(LogSettings.SETTING_OPENLOG_LOGLEVEL, Level.OFF).levelInt;

        int rl = Math.min(cl, Math.min(fl, ol));

        return Level.toLevel(rl);
    }

    private void addError(String msg, Throwable t) {
        statusUtil.addError(this, msg, t);
    }

    private void addInfo(String msg) {
        statusUtil.addInfo(this, msg);
    }

    private void addWarn(String msg) {
        statusUtil.addWarn(this, msg);
    }

    /**
     * Print if there are any status messages above WARN.
     */
    private void printProblems() {
        List<Status> statusList = lc.getStatusManager().getCopyOfStatusList();
        for (Status status : statusList) {
            if (status.getEffectiveLevel() >= ErrorStatus.WARN) {
                System.out.println(status);
            }
        }
    }


}
