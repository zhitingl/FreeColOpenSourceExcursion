/**
 *  Copyright (C) 2002-2014   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol;

import java.awt.Dimension;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.net.JarURLConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.FreeColSeed;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.io.FreeColTcFile;
import net.sf.freecol.common.io.Mods;
import net.sf.freecol.common.logging.DefaultHandler;
import net.sf.freecol.common.model.NationOptions.Advantages;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.networking.NoRouteToServerException;
import net.sf.freecol.common.option.LanguageOption;
import net.sf.freecol.common.option.Option;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.server.FreeColServer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;


/**
 * This class is responsible for handling the command-line arguments
 * and starting either the stand-alone server or the client-GUI.
 *
 * @see net.sf.freecol.client.FreeColClient FreeColClient
 * @see net.sf.freecol.server.FreeColServer FreeColServer
 */
public final class FreeCol {

    private static final Logger logger = Logger.getLogger(FreeCol.class.getName());

    /** The difficulty levels. */
    public static final String[] DIFFICULTIES = new String[] {
        "veryEasy", "easy", "medium", "hard", "veryHard"
    };

    /** The extension for FreeCol saved games. */
    public static final String  FREECOL_SAVE_EXTENSION = ".fsg";

    /** A file filter to select the saved game files. */
    public static final FileFilter freeColSaveFileFilter
        = new FileFilter() {
                public boolean accept(File f) {
                    return f.isFile()
                        && f.getName().endsWith(FREECOL_SAVE_EXTENSION)
                        && f.getName().length() > FREECOL_SAVE_EXTENSION.length();
                }
            };

    public static final String  CLIENT_THREAD = "FreeColClient:";
    public static final String  SERVER_THREAD = "FreeColServer:";
    public static final String  METASERVER_THREAD = "FreeColMetaServer:";

    public static final String  META_SERVER_ADDRESS = "meta.freecol.org";
    public static final int     META_SERVER_PORT = 3540;

    private static final String FREECOL_VERSION = "0.11.0";
    private static String       FREECOL_REVISION;

    // Cli defaults.

    private static final Advantages ADVANTAGES_DEFAULT = Advantages.SELECTABLE;
    private static final String DIFFICULTY_DEFAULT = "model.difficulty.medium";
    private static final int    EUROPEANS_DEFAULT = 4;
    private static final int    EUROPEANS_MIN = 1;
    private static final Level  LOGLEVEL_DEFAULT = Level.INFO;
    private static final String JAVA_VERSION_MIN = "1.6";
    private static final int    MEMORY_MIN = 128; // Mbytes
    private static final int    PORT_DEFAULT = 3541;
    private static final String SPLASH_FILE_DEFAULT = "splash.jpg";
    private static final String TC_DEFAULT = "freecol";
    public static final int     TIMEOUT_DEFAULT = 60; // 1 minute
    public static final int     TIMEOUT_MIN = 10; // 10s


    // Cli values.  Often set to null so the default can be applied in
    // the accessor function.

    private static boolean checkIntegrity = false,
                           consoleLogging = false,
                           debugStart = false,
                           fastStart = false,
                           introVideo = true,
                           javaCheck = true,
                           memoryCheck = true,
                           publicServer = true,
                           sound = true,
                           standAloneServer = false;

    /** The type of advantages. */
    private static Advantages advantages = null;

    /** The difficulty level id. */
    private static String difficulty = null;

    /** The number of European nations to enable by default. */
    private static int europeanCount = EUROPEANS_DEFAULT;

    /** A font override. */
    private static String fontName = null;

    /** The level of logging in this game. */
    private static Level logLevel = LOGLEVEL_DEFAULT;

    /** The client player name. */
    private static String name = null;

    /** How to name and configure the server. */
    private static int serverPort = -1;
    private static String serverName = null;

    /** Where the splash file lives. */
    private static String splashFilename = SPLASH_FILE_DEFAULT;

    /** The TotalConversion / ruleset in play, defaults to "freecol". */
    private static String tc = null;

    /** The time out (seconds) for otherwise blocking commands. */
    private static int timeout = -1;

    /** The size of window to create, defaults to null for full screen. */
    private static Dimension windowSize = null;



    private FreeCol() {} // Hide constructor

    /**
     * The entrypoint.
     *
     * @param args The command-line arguments.
     */
    public static void main(String[] args) {
        FREECOL_REVISION = FREECOL_VERSION;
        try {
            String revision = readVersion(FreeCol.class);
            if (revision != null) {
                FREECOL_REVISION += " (Revision: " + revision + ")";
            }
        } catch (Exception e) {
            System.err.println("Unable to load Manifest: " + e.getMessage());
        }

        // Java bug #7075600 causes BR#2554.  The workaround is to set
        // the following property.  Remove if/when they fix Java.
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        // We can not even emit localized error messages until we find
        // the data directory, which might have been specified on the
        // command line.  Take care to use the *last* instance.
        String dataDirectoryArg = null;
        for (int i = args.length - 2; i >= 0; i--) {
            if ("--freecol-data".equals(args[i])) {
                dataDirectoryArg = args[++i];
                break;
            }
        }
        String err = FreeColDirectories.setDataDirectory(dataDirectoryArg);
        if (err != null) fatal(err); // This must not fail.

        // Now we have the data directory, establish the base locale.
        // Beware, the locale may change!
        String localeArg = null;
        for (int i = args.length - 2; i >= 0; i--) {
            if ("--default-locale".equals(args[i])) {
                localeArg = args[++i];
                break;
            }
        }
        Locale locale;
        if (localeArg == null) {
            locale = Locale.getDefault();
        } else {
            // Strip encoding if present
            int index = localeArg.indexOf('.');
            if (index > 0) localeArg = localeArg.substring(0, index);

            locale = Messages.getLocale(localeArg);
        }

        // Locale established, now load some messages.
        Messages.setMessageBundle(locale);

        // Now we can emit error messages, parse the other command
        // line arguments.
        handleArgs(args);

        // Do the potentially fatal system checks as early as possible.
        String version = System.getProperty("java.version");
        if (javaCheck && JAVA_VERSION_MIN.compareTo(version) > 0) {
            fatal(StringTemplate.template("main.javaVersion")
                .addName("%version%", version)
                .addName("%minVersion%", JAVA_VERSION_MIN));
        }
        long memory = Runtime.getRuntime().maxMemory();
        if (memoryCheck && memory < MEMORY_MIN * 1000000) {
            fatal(StringTemplate.template("main.memory")
                .addAmount("%memory%", memory)
                .addAmount("%minMemory%", MEMORY_MIN));
        }

        // Having parsed the command line args, we know where the user
        // directories should be, so we can set up the rest of the
        // file/directory structure.
        String userMsg = FreeColDirectories.setUserDirectories();

        // Now we have the log file path, start logging.
        final Logger baseLogger = Logger.getLogger("");
        final Handler[] handlers = baseLogger.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            baseLogger.removeHandler(handlers[i]);
        }
        String logFile = FreeColDirectories.getLogFilePath();
        try {
            baseLogger.addHandler(new DefaultHandler(consoleLogging, logFile));
            Logger freecolLogger = Logger.getLogger("net.sf.freecol");
            freecolLogger.setLevel(logLevel);
        } catch (FreeColException e) {
            System.err.println("Logging initialization failure: "
                + e.getMessage());
            e.printStackTrace();
        }
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                public void uncaughtException(Thread thread, Throwable e) {
                    baseLogger.log(Level.WARNING, "Uncaught exception from thread: " + thread, e);
                }
            });

        // Now we can find the client options, allow the options
        // setting to override the locale.  We have users whose
        // machines default to Finnish but play FreeCol in English.
        // If the user has selected automatic language selection, do
        // nothing, since we have already set up the default locale.
        String clientLanguage = ClientOptions.getLanguageOption();
        if (!(clientLanguage == null
                || ClientOptions.AUTOMATIC.equalsIgnoreCase(clientLanguage))) {
            locale = Messages.getLocale(clientLanguage);
            if (!Locale.getDefault().equals(locale)) {
                Messages.setMessageBundle(locale);
                logger.info("Loaded message for " + locale);
            }
        }

        // Now we have the user mods directory and the locale is now
        // stable, load the mods and their messages.
        Mods.loadMods();
        Messages.setModMessageBundle(locale);

        // Report on where we are.
        if (userMsg != null) logger.info(Messages.message(userMsg));
        File autosave = FreeColDirectories.getAutosaveDirectory();
        File clientOptionsFile = FreeColDirectories.getClientOptionsFile();
        File userMods = FreeColDirectories.getUserModsDirectory();
        StringBuilder sb = new StringBuilder(256);
        sb.append("Initialization:")
            .append("\n  java:       ").append(version)
            .append("\n  memory:     ").append(memory)
            .append("\n  locale:     ").append(locale)
            .append("\n  data:       ")
            .append(FreeColDirectories.getDataDirectory().getPath())
            .append("\n  userConfig: ")
            .append(FreeColDirectories.getUserConfigDirectory().getPath())
            .append("\n  userData:   ")
            .append(FreeColDirectories.getUserDataDirectory().getPath())
            .append("\n  autosave:   ")
            .append((autosave == null) ? "NONE" : autosave.getPath())
            .append("\n  logFile:    ")
            .append(FreeColDirectories.getLogFilePath())
            .append("\n  options:    ")
            .append((clientOptionsFile == null) ? "NONE"
                : clientOptionsFile.getPath())
            .append("\n  save:       ")
            .append(FreeColDirectories.getSaveDirectory().getPath())
            .append("\n  userMods:   ")
            .append((userMods == null) ? "NONE" : userMods.getPath());
        logger.info(sb.toString());

        // Ready to specialize into client or server.
        if (standAloneServer) {
            startServer();
        } else {
            startClient(userMsg);
        }
    }

    /**
     * Extract the package version from the class.
     *
     * @param c The <code>Class</code> to extract from.
     * @return A value of the package version attribute.
     */
    private static String readVersion(Class c) throws IOException {
        String resourceName = "/" + c.getName().replace('.', '/') + ".class";
        URL url = c.getResource(resourceName);
        Manifest mf = ((JarURLConnection)url.openConnection()).getManifest();
        return mf.getMainAttributes().getValue("Package-Version");
    }

    /**
     * Exit printing fatal error message.
     *
     * @param template A <code>StringTemplate</code> to print.
     */
    public static void fatal(StringTemplate template) {
        fatal(Messages.message(template));
    }

    /**
     * Exit printing fatal error message.
     *
     * @param err The error message to print.
     */
    public static void fatal(String err) {
        if (err == null || "".equals(err)) {
            err = "Bogus null fatal error message";
            Thread.dumpStack();
        }
        System.err.println(err);
        System.exit(1);
    }

    /**
     * Just gripe to System.err.
     *
     * @param template A <code>StringTemplate</code> to print.
     */
    public static void gripe(StringTemplate template) {
        System.err.println(Messages.message(template));
    }

    /**
     * Just gripe to System.err.
     *
     * @param key A message key.
     */
    public static void gripe(String key) {
        System.err.println(Messages.message(key));
    }

    /**
     * Processes the command-line arguments and takes appropriate
     * actions for each of them.
     *
     * @param args The command-line arguments.
     */
    private static void handleArgs(String[] args) {
        Options options = new Options();
        final String help = Messages.message("cli.help");
        final File dummy = new File("dummy");
        final String argDirectory = Messages.message("cli.arg.directory");

        // Help options.
        options.addOption(OptionBuilder.withLongOpt("usage")
                          .withDescription(help)
                          .create());
        options.addOption(OptionBuilder.withLongOpt("help")
                          .withDescription(help)
                          .create());

        // Special options handled early.
        options.addOption(OptionBuilder.withLongOpt("freecol-data")
                          .withDescription(Messages.message("cli.freecol-data"))
                          .withArgName(argDirectory)
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("default-locale")
                          .withDescription(Messages.message("cli.default-locale"))
                          .withArgName(Messages.message("cli.arg.locale"))
                          .hasArg()
                          .create());

        // Ordinary options, handled here.
        options.addOption(OptionBuilder.withLongOpt("advantages")
                          .withDescription(Messages.message(StringTemplate.template("cli.advantages")
                                  .addName("%advantages%", getValidAdvantages())))
                          .withArgName(Messages.message("cli.arg.advantages"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("check-savegame")
                          .withDescription(Messages.message("cli.check-savegame"))
                          .withArgName(Messages.message("cli.arg.file"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("clientOptions")
                          .withDescription(Messages.message("cli.clientOptions"))
                          .withArgName(Messages.message("cli.arg.clientOptions"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("debug")
                          .withDescription(Messages.message(StringTemplate.template("cli.debug")
                                  .addName("%modes%", FreeColDebugger.getDebugModes())))
                          .withArgName(Messages.message("cli.arg.debug"))
                          .hasOptionalArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("debug-run")
                          .withDescription(Messages.message("cli.debug-run"))
                          .withArgName(Messages.message("cli.arg.debugRun"))
                          .hasOptionalArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("debug-start")
                          .withDescription(Messages.message("cli.debug-start"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("difficulty")
                          .withDescription(Messages.message("cli.difficulty"))
                          .withArgName(Messages.message("cli.arg.difficulty"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("europeans")
                          .withDescription(Messages.message("cli.european-count"))
                          .withArgName(Messages.message("cli.arg.europeans"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("fast")
                          .withDescription(Messages.message("cli.fast"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("font")
                          .withDescription(Messages.message("cli.font"))
                          .withArgName(Messages.message("cli.arg.font"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("load-savegame")
                          .withDescription(Messages.message("cli.load-savegame"))
                          .withArgName(Messages.message("cli.arg.file"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("log-console")
                          .withDescription(Messages.message("cli.log-console"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("log-file")
                          .withDescription(Messages.message("cli.log-file"))
                          .withArgName(Messages.message("cli.arg.name"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("log-level")
                          .withDescription(Messages.message("cli.log-level"))
                          .withArgName(Messages.message("cli.arg.loglevel"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("name")
                          .withDescription(Messages.message("cli.name"))
                          .withArgName(Messages.message("cli.arg.name"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("no-intro")
                          .withDescription(Messages.message("cli.no-intro"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("no-java-check")
                          .withDescription(Messages.message("cli.no-java-check"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("no-memory-check")
                          .withDescription(Messages.message("cli.no-memory-check"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("no-sound")
                          .withDescription(Messages.message("cli.no-sound"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("private")
                          .withDescription(Messages.message("cli.private"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("seed")
                          .withDescription(Messages.message("cli.seed"))
                          .withArgName(Messages.message("cli.arg.seed"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("server")
                          .withDescription(Messages.message("cli.server"))
                          .withArgName(Messages.message("cli.arg.port"))
                          .hasOptionalArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("server-name")
                          .withDescription(Messages.message("cli.server-name"))
                          .withArgName(Messages.message("cli.arg.name"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("splash")
                          .withDescription(Messages.message("cli.splash"))
                          .withArgName(Messages.message("cli.arg.file"))
                          .hasOptionalArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("tc")
                          .withDescription(Messages.message("cli.tc"))
                          .withArgName(Messages.message("cli.arg.name"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("timeout")
                          .withDescription(Messages.message("cli.timeout"))
                          .withArgName(Messages.message("cli.arg.timeout"))
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("user-cache-directory")
                          .withDescription(Messages.message("cli.user-cache-directory"))
                          .withArgName(argDirectory)
                          .withType(dummy)
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("user-config-directory")
                          .withDescription(Messages.message("cli.user-config-directory"))
                          .withArgName(argDirectory)
                          .withType(dummy)
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("user-data-directory")
                          .withDescription(Messages.message("cli.user-data-directory"))
                          .withArgName(argDirectory)
                          .withType(dummy)
                          .hasArg()
                          .create());
        options.addOption(OptionBuilder.withLongOpt("version")
                          .withDescription(Messages.message("cli.version"))
                          .create());
        options.addOption(OptionBuilder.withLongOpt("windowed")
                          .withDescription(Messages.message("cli.windowed"))
                          .withArgName(Messages.message("cli.arg.dimensions"))
                          .hasOptionalArg()
                          .create());

        CommandLineParser parser = new PosixParser();
        boolean usageError = false;
        try {
            CommandLine line = parser.parse(options, args);
            if (line.hasOption("help") || line.hasOption("usage")) {
                printUsage(options, 0);
            }

            if (line.hasOption("default-locale")) {
                ; // Do nothing, already handled in main().
            }
            if (line.hasOption("freecol-data")) {
                ; // Do nothing, already handled in main().
            }

            if (line.hasOption("advantages")) {
                String arg = line.getOptionValue("advantages");
                Advantages a = selectAdvantages(arg);
                if (a == null) {
                    fatal(StringTemplate.template("cli.error.advantages")
                        .addName("%advantages%", getValidAdvantages())
                        .addName("%arg%", arg));
                }
            }

            if (line.hasOption("check-savegame")) {
                String arg = line.getOptionValue("check-savegame");
                if (!FreeColDirectories.setSavegameFile(arg)) {
                    fatal(StringTemplate.template("cli.err.save")
                        .addName("%string%", arg));
                }
                checkIntegrity = true;
                standAloneServer = true;
            }

            if (line.hasOption("clientOptions")) {
                String fileName = line.getOptionValue("clientOptions");
                if (!FreeColDirectories.setClientOptionsFile(fileName)) {
                    // Not fatal.
                    gripe(StringTemplate.template("cli.error.clientOptions")
                        .addName("%string%", fileName));
                }
            }

            if (line.hasOption("debug")) {
                // If the optional argument is supplied use limited mode.
                String arg = line.getOptionValue("debug");
                if (arg == null || "".equals(arg)) {
                    // Let empty argument default to menus functionality.
                    arg = FreeColDebugger.DebugMode.MENUS.toString();
                }
                if (!FreeColDebugger.setDebugModes(arg)) { // Not fatal.
                    gripe(StringTemplate.template("cli.error.debug")
                        .addName("%modes%", FreeColDebugger.getDebugModes()));
                }
                // user set log level has precedence
                if (!line.hasOption("log-level")) logLevel = Level.FINEST;
            }
            if (line.hasOption("debug-run")) {
                FreeColDebugger.enableDebugMode(FreeColDebugger.DebugMode.MENUS);
                FreeColDebugger.configureDebugRun(line.getOptionValue("debug-run"));
            }
            if (line.hasOption("debug-start")) {
                debugStart = true;
                FreeColDebugger.enableDebugMode(FreeColDebugger.DebugMode.MENUS);
            }

            if (line.hasOption("difficulty")) {
                String arg = line.getOptionValue("difficulty");
                String difficulty = selectDifficulty(arg);
                if (difficulty == null) {
                    fatal(StringTemplate.template("cli.error.difficulties")
                        .addName("%difficulties%", getValidDifficulties())
                        .addName("%arg%", arg));
                }
            }

            if (line.hasOption("europeans")) {
                int e = selectEuropeanCount(line.getOptionValue("europeans"));
                if (e < 0) {
                    gripe(StringTemplate.template("cli.error.europeans")
                        .addAmount("%min%", EUROPEANS_MIN));
                }
            }

            if (line.hasOption("fast")) {
                fastStart = true;
                introVideo = false;
            }

            if (line.hasOption("font")) {
                fontName = line.getOptionValue("font");
            }

            if (line.hasOption("load-savegame")) {
                String arg = line.getOptionValue("load-savegame");
                if (!FreeColDirectories.setSavegameFile(arg)) {
                    fatal(StringTemplate.template("cli.error.save")
                        .addName("%string%", arg));
                }
            }

            if (line.hasOption("log-console")) {
                consoleLogging = true;
            }
            if (line.hasOption("log-file")) {
                FreeColDirectories.setLogFilePath(line.getOptionValue("log-file"));
            }
            if (line.hasOption("log-level")) {
                setLogLevel(line.getOptionValue("log-level"));
            }

            if (line.hasOption("name")) {
                setName(line.getOptionValue("name"));
            }

            if (line.hasOption("no-intro")) {
                introVideo = false;
            }
            if (line.hasOption("no-java-check")) {
                javaCheck = false;
            }
            if (line.hasOption("no-memory-check")) {
                memoryCheck = false;
            }
            if (line.hasOption("no-sound")) {
                sound = false;
            }

            if (line.hasOption("private")) {
                publicServer = false;
            }

            if (line.hasOption("server")) {
                String arg = line.getOptionValue("server");
                if (!setServerPort(arg)) {
                    fatal(StringTemplate.template("cli.error.serverPort")
                        .addName("%string%", arg));
                }
                standAloneServer = true;
            }
            if (line.hasOption("server-name")) {
                serverName = line.getOptionValue("server-name");
            }

            if (line.hasOption("seed")) {
                FreeColSeed.setFreeColSeed(line.getOptionValue("seed"));
            }

            if (line.hasOption("splash")) {
                splashFilename = line.getOptionValue("splash");
            }

            if (line.hasOption("tc")) {
                setTC(line.getOptionValue("tc")); // Failure is deferred.
            }

            if (line.hasOption("timeout")) {
                String arg = line.getOptionValue("timeout");
                if (!setTimeout(arg)) { // Not fatal
                    gripe(StringTemplate.template("cli.error.timeout")
                        .addName("%string%", arg)
                        .addName("%minimum%", Integer.toString(TIMEOUT_MIN)));
                }
            }

            if (line.hasOption("user-cache-directory")) {
                String arg = line.getOptionValue("user-cache-directory");
                String errMsg = FreeColDirectories.setUserCacheDirectory(arg);
                if (errMsg != null) gripe(errMsg); // Not fatal.
            }

            if (line.hasOption("user-config-directory")) {
                String arg = line.getOptionValue("user-config-directory");
                String errMsg = FreeColDirectories.setUserConfigDirectory(arg);
                if (errMsg != null) gripe(errMsg); // Not fatal.
            }

            if (line.hasOption("user-data-directory")) {
                String arg = line.getOptionValue("user-data-directory");
                String errMsg = FreeColDirectories.setUserDataDirectory(arg);
                if (errMsg != null) { // Fatal, unable to save.
                    fatal(StringTemplate.template(errMsg)
                        .addName("%string%", arg));
                }
            }

            if (line.hasOption("version")) {
                System.out.println("FreeCol " + getVersion());
                System.exit(0);
            }

            if (line.hasOption("windowed")) {
                String arg = line.getOptionValue("windowed");
                setWindowSize(arg); // Does not fail
            }

        } catch (ParseException e) {
            System.err.println("\n" + e.getMessage() + "\n");
            usageError = true;
        }
        if (usageError) printUsage(options, 1);
    }

    /**
     * Prints the usage message and exits.
     *
     * @param options The command line <code>Options</code>.
     * @param status The status to exit with.
     */
    private static void printUsage(Options options, int status) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -Xmx 256M -jar freecol.jar [OPTIONS]",
                            options);
        System.exit(status);
    }

    /**
     * Get the specification from the specified TC.
     *
     * @return A <code>Specification</code>.  Quits on error.
     */
    private static Specification getTCSpecification() {
        Specification spec;
        try {
            FreeColTcFile tcf = FreeCol.getTCFile();
            if (tcf == null) {
                spec = null;
            } else {
                spec = tcf.getSpecification();
                spec.applyDifficultyLevel(FreeCol.getDifficulty());
            }
        } catch (IOException ioe) {
            spec = null;
        }
        if (spec == null) {
            fatal(StringTemplate.template("cli.error.badTC")
                .addName("%tc%", getTC()));
        }
        return spec;
    }


    // Accessors, mutators and support for the cli variables.

    /**
     * Gets the default advantages type.
     *
     * @return Usually Advantages.SELECTABLE, but can be overridden at the
     *     command line.
     */
    public static Advantages getAdvantages() {
        return (advantages == null) ? ADVANTAGES_DEFAULT
            : advantages;
    }

    /**
     * Sets the advantages type.
     *
     * Called from NewPanel when a selection is made.
     *
     * @param advantages The name of the new advantages type.
     * @return The type of advantages set, or null if none.
     */
    private static Advantages selectAdvantages(String advantages) {
        for (Advantages a : Advantages.values()) {
            String msg = Messages.getName(a);
            if (msg.equals(advantages)) {
                setAdvantages(a);
                return a;
            }
        }
        return null;
    }

    /**
     * Sets the advantages type.
     *
     * @param advantages The new <code>Advantages</code> type.
     */
    public static void setAdvantages(Advantages advantages) {
        FreeCol.advantages = advantages;
    }

    /**
     * Gets a comma separated list of localized advantage type names.
     *
     * @return A list of advantage types.
     */
    private static String getValidAdvantages() {
        String ret = "";
        for (Advantages a : Advantages.values()) {
            ret += "," + Messages.getName(a);
        }
        return ret.substring(1);
    }

    /**
     * Gets the difficulty level.
     *
     * @return The name of a difficulty level.
     */
    public static String getDifficulty() {
        return (difficulty == null) ? DIFFICULTY_DEFAULT : difficulty;
    }

    /**
     * Selects a difficulty level.
     *
     * @param arg The supplied difficulty argument.
     * @return The name of the selected difficulty, or null if none.
     */
    public static String selectDifficulty(String arg) {
        for (String d : DIFFICULTIES) {
            String key = "model.difficulty." + d;
            String value = Messages.message(key + ".name");
            if (value.equals(arg)) {
                setDifficulty(key);
                return key;
            }
        }
        return null;
    }

    /**
     * Sets the difficulty level.
     *
     * @param difficulty The actual <code>OptionGroup</code>
     *     containing the difficulty level.
     */
    public static void setDifficulty(OptionGroup difficulty) {
        setDifficulty(difficulty.getId());
    }

    /**
     * Sets the difficulty level.
     *
     * @param difficulty The new difficulty.
     */
    public static void setDifficulty(String difficulty) {
        FreeCol.difficulty = difficulty;
    }

    /**
     * Gets the names of the valid difficulty levels.
     *
     * @return The valid difficulty levels, comma separated.
     */
    public static String getValidDifficulties() {
        String s = "";
        for (String d : DIFFICULTIES) {
            String key = "model.difficulty." + d;
            String value = Messages.message(key + ".name");
            s += "," + value;
        }
        return s.substring(1);
    }

    /**
     * Get the number of European nations to enable by default.
     */
    public static int getEuropeanCount() {
        return europeanCount;
    }

    /**
     * Sets the number of enabled European nations.
     *
     * @param n The number of nations to enable.
     */
    public static void setEuropeanCount(int n) {
        europeanCount = n;
    }

    /**
     * Selects a European nation count.
     *
     * @param arg The supplied count argument.
     * @return A valid nation number, or negative on error.
     */
    public static int selectEuropeanCount(String arg) {
        try {
            int n = Integer.parseInt(arg);
            if (n >= EUROPEANS_MIN) {
                setEuropeanCount(n);
                return n;
            }
        } catch (NumberFormatException nfe) {}
        return -1;
    }

    /**
     * Sets the log level.
     *
     * @param arg The log level to set.
     */
    private static void setLogLevel(String arg) {
        logLevel = Level.parse(arg.toUpperCase());
    }

    /**
     * Gets the user name.
     *
     * @return The user name, defaults to the user.name property, then to
     *     the "defaultPlayerName" message value.
     */
    public static String getName() {
        return (name != null) ? name
            : System.getProperty("user.name",
                                 Messages.message("defaultPlayerName"));
    }

    /**
     * Sets the user name.
     *
     * @param name The new user name.
     */
    public static void setName(String name) {
        FreeCol.name = name;
        logger.info("Set FreeCol.name = " + name);
    }

    /**
     * Gets the current revision of game.
     *
     * @return The current version and SVN Revision of the game.
     */
    public static String getRevision() {
        return FREECOL_REVISION;
    }

    /**
     * Gets the server network port.
     *
     * @return The port number.
     */
    public static int getServerPort() {
        return (serverPort < 0) ? PORT_DEFAULT : serverPort;
    }

    /**
     * Sets the server port.
     *
     * @param arg The server port number.
     * @return True if the port was set.
     */
    public static boolean setServerPort(String arg) {
        if (arg == null) return false;
        try {
            serverPort = Integer.parseInt(arg);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    /**
     * Gets the current Total-Conversion.
     *
     * @return Usually TC_DEFAULT, but can be overridden at the command line.
     */
    public static String getTC() {
        return (tc == null) ? TC_DEFAULT : tc;
    }

    /**
     * Sets the Total-Conversion.
     *
     * Called from NewPanel when a selection is made.
     *
     * @param tc The name of the new total conversion.
     */
    public static void setTC(String tc) {
        FreeCol.tc = tc;
    }

    /**
     * Gets the FreeColTcFile for the current TC.
     *
     * @return The <code>FreeColTcFile</code>.
     */
    public static FreeColTcFile getTCFile() {
        try {
            return new FreeColTcFile(getTC());
        } catch (IOException ioe) {}
        return null;
    }

    /**
     * Gets the timeout.
     * Use the command line specified one if any, otherwise default
     * to `infinite' in single player and the TIMEOUT_DEFAULT for
     * multiplayer.
     *
     * @param singlePlayer True if this is a single player game.
     * @return A suitable timeout value.
     */
    public static int getTimeout(boolean singlePlayer) {
        return (timeout >= TIMEOUT_MIN) ? timeout
            : (singlePlayer) ? Integer.MAX_VALUE
            : TIMEOUT_DEFAULT;
    }

    /**
     * Sets the timeout.
     *
     * @param timeout A string containing the new timeout.
     * @return True if the timeout was set.
     */
    public static boolean setTimeout(String timeout) {
        try {
            int result = Integer.parseInt(timeout);
            if (result >= TIMEOUT_MIN) {
                FreeCol.timeout = result;
                return true;
            }
        } catch (NumberFormatException nfe) {}
        return false;
    }

    /**
     * Gets the current version of game.
     *
     * @return The current version of the game using the format "x.y.z",
     *         where "x" is major, "y" is minor and "z" is revision.
     */
    public static String getVersion() {
        return FREECOL_VERSION;
    }

    /**
     * Sets the window size.
     *
     * Does not fail because any empty or invalid value is interpreted as
     * `windowed but use as much screen as possible'.
     *
     * @param arg The window size specification.
     */
    public static void setWindowSize(String arg) {
        String[] xy;
        if (arg != null
            && (xy = arg.split("[^0-9]")) != null
            && xy.length == 2) {
            try {
                windowSize = new Dimension(Integer.parseInt(xy[0]),
                                           Integer.parseInt(xy[1]));
            } catch (NumberFormatException nfe) {}
        }
        if (windowSize == null) windowSize = new Dimension(-1, -1);
    }


    // The major final actions.

    /**
     * Start a client.
     *
     * @param userMsg An optional user message key.
     */
    private static void startClient(String userMsg) {
        Specification spec = null;
        File savegame = FreeColDirectories.getSavegameFile();
        if (debugStart) {
            spec = FreeCol.getTCSpecification();
        } else if (fastStart) {
            if (savegame == null) {
                // continue last saved game if possible,
                // otherwise start a new one
                savegame = FreeColDirectories.getLastSaveGameFile();
                if (savegame == null) {
                    spec = FreeCol.getTCSpecification();
                }
            }
            // savegame was specified on command line
        }
        new FreeColClient(savegame, windowSize,
                          sound, splashFilename, introVideo, fontName,
                          userMsg, spec);
    }

    /**
     * Start the server.
     */
    private static void startServer() {
        logger.info("Starting stand-alone server.");
        final FreeColServer freeColServer;
        File saveGame = FreeColDirectories.getSavegameFile();
        if (saveGame != null) {
            try {
                final FreeColSavegameFile fis
                    = new FreeColSavegameFile(saveGame);
                freeColServer = new FreeColServer(fis, (Specification)null,
                                                  serverPort, serverName);
                if (checkIntegrity) {
                    boolean integrityOK = freeColServer.getIntegrity() > 0;
                    gripe((integrityOK)
                        ? "cli.check-savegame.success"
                        : "cli.check-savegame.failure");
                    System.exit((integrityOK) ? 0 : 2);
                }
            } catch (Exception e) {
                if (checkIntegrity) gripe("cli.check-savegame.failure");
                fatal(Messages.message("server.load")
                    + ": " + e.getMessage());
                return;
            }
        } else {
            Specification spec = FreeCol.getTCSpecification();
            try {
                // TODO: command line advantages setting?
                freeColServer = new FreeColServer(publicServer, false, spec,
                                                  serverPort, serverName);
            } catch (NoRouteToServerException nrtse) {
                fatal(Messages.message("server.noRouteToServer"));
                return;
            } catch (Exception e) {
                fatal(Messages.message("server.initialize")
                    + ": " + e.getMessage());
                return;
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    freeColServer.getController().shutdown();
                }
            });
    }
}
