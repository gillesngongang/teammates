package teammates.e2e.cases.e2e;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import teammates.common.datatransfer.DataBundle;
import teammates.common.util.AppUrl;
import teammates.common.util.Const;
import teammates.common.util.Url;
import teammates.e2e.cases.BaseTestCaseWithBackDoorApiAccess;
import teammates.e2e.pageobjects.AdminHomePage;
import teammates.e2e.pageobjects.AppPage;
import teammates.e2e.pageobjects.Browser;
import teammates.e2e.pageobjects.BrowserPool;
import teammates.e2e.pageobjects.HomePage;
import teammates.e2e.pageobjects.LoginPage;
import teammates.e2e.util.TestProperties;

/**
 * Base class for all browser tests.
 *
 * <p>This type of test has no knowledge of the workings of the application,
 * and can only communicate via the UI or via {@link teammates.e2e.util.BackDoor} to obtain/transmit data.
 */
public abstract class BaseE2ETestCase extends BaseTestCaseWithBackDoorApiAccess {

    protected Browser browser;
    protected DataBundle testData;

    @BeforeClass
    public void baseClassSetup() throws Exception {
        prepareTestData();
        prepareBrowser();
    }

    protected void prepareBrowser() {
        browser = BrowserPool.getBrowser();
    }

    protected abstract void prepareTestData() throws Exception;

    @Override
    protected String getTestDataFolder() {
        return TestProperties.TEST_DATA_FOLDER;
    }

    @AfterClass(alwaysRun = true)
    public void baseClassTearDown() {
        releaseBrowser();
    }

    protected void releaseBrowser() {
        if (browser == null) {
            return;
        }
        BrowserPool.release(browser);
    }

    /**
     * Creates an {@link AppUrl} for the supplied {@code relativeUrl} parameter.
     * The base URL will be the value of test.app.url in test.properties.
     * {@code relativeUrl} must start with a "/".
     */
    protected static AppUrl createUrl(String relativeUrl) {
        return new AppUrl(TestProperties.TEAMMATES_URL + relativeUrl);
    }

    /**
     * Creates a {@link Url} to navigate to the file named {@code testFileName}
     * inside {@link TestProperties#TEST_PAGES_FOLDER}.
     * {@code testFileName} must start with a "/".
     */
    protected static Url createLocalUrl(String testFileName) throws IOException {
        return new Url("file:///" + new File(".").getCanonicalPath() + "/"
                                  + TestProperties.TEST_PAGES_FOLDER + testFileName);
    }

    /**
     * Logs in a page using admin credentials (i.e. in masquerade mode).
     */
    protected <T extends AppPage> T loginAdminToPage(AppUrl url, Class<T> typeOfPage) {

        if (browser.isAdminLoggedIn) {
            browser.driver.get(url.toAbsoluteString());
            try {
                return AppPage.getNewPageInstance(browser, typeOfPage);
            } catch (Exception e) {
                //ignore and try to logout and login again if fail.
                ignorePossibleException();
            }
        }

        // logout and attempt to load the requested URL. This will be
        // redirected to a dev-server/google login page
        logout();
        browser.driver.get(url.toAbsoluteString());

        String adminUsername = TestProperties.TEST_ADMIN_ACCOUNT;
        String adminPassword = TestProperties.TEST_ADMIN_PASSWORD;

        String userId = url.get(Const.ParamsNames.USER_ID);

        if (TestProperties.isDevServer() && userId != null) {
            // This workaround is necessary because the front-end has not been optimized
            // to enable masquerade mode yet
            adminUsername = userId;
        }

        // login based on the login page type
        LoginPage loginPage = AppPage.createCorrectLoginPageType(browser);
        loginPage.loginAsAdmin(adminUsername, adminPassword);

        // After login, the browser should be redirected to the page requested originally.
        // No need to reload. In fact, reloading might results in duplicate request to the server.
        return AppPage.getNewPageInstance(browser, typeOfPage);
    }

    /**
     * TODO legacy method to be removed after migration of UI tests.
     */
    @Deprecated
    protected <T extends teammates.test.pageobjects.AppPage> T loginAdminToPageOld(AppUrl url, Class<T> typeOfPage) {
        return teammates.test.pageobjects.AppPage.getNewPageInstance(browser, typeOfPage);
    }

    /**
     * Navigates to the application's home page (as defined in test.properties)
     * and gives the {@link HomePage} instance based on it.
     */
    protected HomePage getHomePage() {
        HomePage homePage = AppPage.getNewPageInstance(browser, createUrl(""), HomePage.class);
        homePage.waitForPageToLoad();
        return homePage;
    }

    /**
     * Equivalent to clicking the 'logout' link in the top menu of the page.
     */
    protected void logout() {
        browser.driver.get(createUrl(Const.ResourceURIs.LOGOUT).toAbsoluteString());
        AppPage.getNewPageInstance(browser, HomePage.class).waitForPageToLoad();
        browser.isAdminLoggedIn = false;
    }

    protected AdminHomePage loginAdmin() {
        return loginAdminToPage(createUrl(Const.WebPageURIs.ADMIN_HOME_PAGE), AdminHomePage.class);
    }

}
