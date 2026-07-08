import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SeleniumTest {
    private WebDriver webDriver;
    private Process httpServerProcess;

    @BeforeEach
    public void setUp() throws Exception {
        // Suppress Selenium warnings
        java.util.logging.Logger.getLogger("org.openqa.selenium")
                .setLevel(java.util.logging.Level.OFF);

        // Find chromedriver (Linux and Windows paths)
        String[] driverPaths = {
                // Linux / Mac paths
                "/usr/bin/chromedriver",
                "/usr/local/bin/chromedriver",
                "/snap/bin/chromedriver",
                "./driver/chromedriver",
                "/opt/chromedriver/chromedriver",
                // Windows paths
                ".\\driver\\chromedriver.exe",
                "C:\\chromedriver\\chromedriver.exe",
                "C:\\Program Files\\chromedriver\\chromedriver.exe",
                (System.getenv("USERPROFILE") == null ? null : System.getenv("USERPROFILE") + "\\chromedriver.exe"),
                (System.getenv("LOCALAPPDATA") == null ? null : System.getenv("LOCALAPPDATA") + "\\chromedriver\\chromedriver.exe")
        };

        for (String path : driverPaths) {
            if (path == null) continue;
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                System.setProperty("webdriver.chrome.driver", path);
                break;
            }
        }

        ChromeOptions options = new ChromeOptions();

        // Find and set Chrome binary (Linux and Windows paths)
        String[] chromePaths = {
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium",
                "/usr/bin/google-chrome",
                "/snap/bin/chromium",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                (System.getenv("LOCALAPPDATA") == null ? null : System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe"),
                (System.getenv("PROGRAMFILES") == null ? null : System.getenv("PROGRAMFILES") + "\\Google\\Chrome\\Application\\chrome.exe")
        };

        for (String path : chromePaths) {
            if (path == null) continue;
            if (new File(path).exists()) {
                options.setBinary(path);
                break;
            }
        }

        // Browser arguments (commit/CI-safe + file:// compatible)
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-features=VizDisplayCompositor",
                "--use-gl=swiftshader",
                "--allow-file-access-from-files",
                "--disable-web-security",
                "--user-data-dir=/tmp/chrome-test-" + System.currentTimeMillis()
        );

        // Create the WebDriver ONCE
        webDriver = new ChromeDriver(options);

        // Try Python HTTP server first, fall back to file:// if unavailable
        File htmlFile = new File("src/main/StyledPage.html").getCanonicalFile();
        try {
            String htmlUrl = startHttpServer(htmlFile);
            webDriver.get(htmlUrl);
        } catch (Exception e) {
            System.out.println("HTTP server unavailable, falling back to file://");
            webDriver.get(htmlFile.toURI().toString());
        }

        new WebDriverWait(webDriver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

        // Fix occasional headless rendering issues
        Thread.sleep(1500);
        ((JavascriptExecutor) webDriver).executeScript(
                "document.body.style.display='none';" +
                        "document.body.offsetHeight;" +
                        "document.body.style.display='block';"
        );
    }

    private String startHttpServer(File htmlFile) throws Exception {
        int port = 8000 + (int) (Math.random() * 1000);
        String directory = htmlFile.getParent();
        String fileName = htmlFile.getName();

        // Use 'python' on Windows, 'python3' on Linux/Mac
        String os = System.getProperty("os.name").toLowerCase();
        String pythonCmd = os.contains("win") ? "python" : "python3";

        ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-m", "http.server", String.valueOf(port));
        pb.directory(new File(directory));
        pb.redirectErrorStream(true);

        httpServerProcess = pb.start();
        Thread.sleep(1500);

        if (!httpServerProcess.isAlive()) {
            throw new RuntimeException("HTTP server failed to start");
        }

        String url = "http://localhost:" + port + "/" + fileName;

        // Test connectivity
        for (int i = 0; i < 10; i++) {
            try {
                java.net.URL testUrl = new java.net.URL(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) testUrl.openConnection();
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                connection.setRequestMethod("HEAD");
                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode == 200) {
                    return url;
                }
            } catch (Exception e) {
                if (i == 9) {
                    throw new RuntimeException("HTTP server not responding: " + e.getMessage());
                }
                Thread.sleep(400);
            }
        }

        throw new RuntimeException("HTTP server failed to respond");
    }

    @AfterEach
    public void tearDown() {
        if (httpServerProcess != null) {
            httpServerProcess.destroy();
            try {
                httpServerProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                httpServerProcess.destroyForcibly();
            }
        }
        if (webDriver != null) {
            webDriver.quit();
        }
    }

    @Test
    public void testH1Color() {
        WebElement h1 = webDriver.findElement(By.tagName("h1"));
        String color = h1.getCssValue("color");
        assertTrue(color.contains("0, 0, 255"), "Expected <h1> to be blue.");
    }

    @Test
    public void testHighlightBackground() {
        WebElement highlight = webDriver.findElement(By.className("highlight"));
        String bg = highlight.getCssValue("background-color");
        assertTrue(bg.contains("255, 255, 0"), "Expected .highlight to have yellow background.");
    }

    @Test
    public void testMainTitleUppercase() {
        WebElement title = webDriver.findElement(By.id("main-title"));
        String transform = title.getCssValue("text-transform");
        assertEquals("uppercase", transform, "Expected #main-title text to be uppercase.");
    }
}
