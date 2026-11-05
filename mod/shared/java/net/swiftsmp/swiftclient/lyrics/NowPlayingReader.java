package net.swiftsmp.swiftclient.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.swiftsmp.swiftclient.SwiftClientMod;

/**
 * Reads the small now-playing file written by the Windows PowerShell bridge or the Android app.
 * On Windows this also keeps that bridge alive, so a SwiftClient install needs no extra setup.
 */
public final class NowPlayingReader {
	private static final long READ_INTERVAL_MS = 30;
	private static final long STALE_MS = 5000;
	private static final long HELPER_RETRY_MS = 10000;
	private static final String SCRIPT_RESOURCE = "/assets/swiftclient/nowplaying.ps1";

	private final Path dir;
	private final Path jsonFile;
	private final Path commandFile;
	private final Path scriptFile;
	private final boolean fixedPath;
	private final boolean windows;

	private volatile NowPlaying current = NowPlaying.NONE;
	private long lastMtime;
	private long lastReadMs;
	private Process helper;
	private long lastHelperAttemptMs;
	private long helperStartedMs;
	private boolean loggedMissingScript;
	private boolean loggedStartFailure;

	public NowPlayingReader() {
		String override = System.getProperty("swiftclient.nowPlayingPath", "").trim();
		fixedPath = !override.isEmpty();
		Path path = fixedPath
			? Path.of(override)
			: FabricLoader.getInstance().getGameDir().resolve("swiftclient");
		if (fixedPath && Files.isDirectory(path)) path = path.resolve("nowplaying.json");
		dir = fixedPath ? path.getParent() : path;
		jsonFile = fixedPath ? path : dir.resolve("nowplaying.json");
		commandFile = dir.resolve("media-command.txt");
		scriptFile = dir.resolve("nowplaying.ps1");
		windows = isWindows();
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "SwiftClient-Media-Stop"));
	}

	public synchronized NowPlaying poll() {
		long now = System.currentTimeMillis();
		if (now - lastReadMs >= READ_INTERVAL_MS) {
			lastReadMs = now;
			readFile(now);
			if (windows && !fixedPath) maintainHelper(now);
		}
		return current;
	}

	public Path directory() {
		return dir;
	}

	public synchronized void command(String command) {
		if (command == null || command.isEmpty()) return;
		try {
			Files.createDirectories(dir);
			Files.writeString(commandFile, command, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			SwiftClientMod.LOG.warn("Could not send media command: {}", e.toString());
		}
	}

	private void readFile(long now) {
		try {
			if (!Files.isRegularFile(jsonFile)) {
				// The bridge writes via a temp file + atomic rename, so the destination can be
				// momentarily absent. Dropping to NONE here made the grace period hold the old
				// line and delayed the next transition; keep the last frame through the gap.
				return;
			}
			long mtime = Files.getLastModifiedTime(jsonFile).toMillis();
			if (now - mtime > STALE_MS) {
				current = NowPlaying.NONE;
				return;
			}
			if (mtime == lastMtime && current.active) return;
			String text = Files.readString(jsonFile, StandardCharsets.UTF_8);
			if (text.isBlank()) return;
			JsonObject object = JsonParser.parseString(text).getAsJsonObject();
			current = NowPlaying.fromJson(object, now);
			lastMtime = mtime;
		} catch (Exception e) {
			// The writer uses an atomic rename, so a partial read should be rare. Keep the previous frame.
		}
	}

	private void maintainHelper(long now) {
		boolean helperAlive = helper != null && helper.isAlive();
		try {
			boolean fresh = Files.isRegularFile(jsonFile)
				&& now - Files.getLastModifiedTime(jsonFile).toMillis() <= STALE_MS;
			if (fresh) return;
			if (helperAlive) {
				// Give a fresh process time to load PowerShell and write its first snapshot.
				if (now - helperStartedMs < 8000) return;
				// A live PowerShell process that has stopped writing is hung; restart it.
				helper.destroy();
				helper = null;
			}
			if (now - lastHelperAttemptMs < HELPER_RETRY_MS) return;
			lastHelperAttemptMs = now;
			startHelper();
		} catch (IOException ignored) {
			// File races are handled on the next poll.
		}
	}

	private void startHelper() {
		if (System.getProperty("swiftclient.noMediaBridge", "").equals("true")) return;
		try {
			Files.createDirectories(dir);
			ensureScript();
			Path log = dir.resolve("nowplaying.log");
			String powershell = System.getenv("SystemRoot");
			powershell = powershell == null
				? "powershell.exe"
				: powershell + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
			ProcessBuilder builder = new ProcessBuilder(
				powershell,
				"-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
				"-File", scriptFile.toAbsolutePath().toString(),
				"-OutDir", dir.toAbsolutePath().toString()
			);
			builder.directory(dir.toFile());
			builder.redirectErrorStream(true);
			builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
			helper = builder.start();
			helperStartedMs = System.currentTimeMillis();
			loggedStartFailure = false;
			SwiftClientMod.LOG.info("SwiftClient media bridge started");
		} catch (Exception e) {
			helper = null;
			if (!loggedStartFailure) {
				loggedStartFailure = true;
				SwiftClientMod.LOG.warn("Could not start the Windows music bridge: {}", e.toString());
			}
		}
	}

	private void ensureScript() throws IOException {
		try (InputStream in = NowPlayingReader.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (in == null) {
				if (!loggedMissingScript) {
					loggedMissingScript = true;
					SwiftClientMod.LOG.warn("Now-playing bridge script is missing from the mod jar");
				}
				return;
			}
			byte[] bytes = in.readAllBytes();
			if (Files.isRegularFile(scriptFile) && Files.size(scriptFile) == bytes.length) return;
			Path tmp = dir.resolve("nowplaying.ps1.tmp");
			Files.write(tmp, bytes);
			Files.move(tmp, scriptFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
	}

	public synchronized void stop() {
		if (helper != null) {
			helper.destroy();
			try {
				helper.waitFor(500, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			helper = null;
		}
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
	}
}
