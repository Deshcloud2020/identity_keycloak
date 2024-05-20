import LoginPage from "../support/pages/LoginPage";
import SidebarPage from "../support/pages/admin-ui/SidebarPage";
import Masthead from "../support/pages/admin-ui/Masthead";
import { keycloakBefore } from "../support/util/keycloak_hooks";

const loginPage = new LoginPage();
const masthead = new Masthead();
const sidebarPage = new SidebarPage();
const helpLabel = ".pf-v5-c-form__group-label-help";

describe("Masthead tests", () => {
  beforeEach(() => {
    loginPage.logIn();
    keycloakBefore();
  });

  describe("Desktop view", () => {
    it("Go to account console and back to admin console", () => {
      sidebarPage.waitForPageLoad();
      masthead.accountManagement();
      cy.url().should("contain", "/realms/master/account");
    });

    it("Sign out reachs to log in screen", () => {
      sidebarPage.waitForPageLoad();
      masthead.signOut();
      sidebarPage.waitForPageLoad();
      loginPage.isLogInPage();
    });

    it("Go to realm info", () => {
      sidebarPage.goToClients();
      masthead.toggleUsernameDropdown().clickRealmInfo();
      cy.get(".pf-v5-l-grid").should("contain.text", "Welcome");
    });

    it("Should go to documentation page", () => {
      masthead.clickGlobalHelp();
      masthead
        .getDocumentationLink()
        .invoke("attr", "href")
        .then((href) => {
          if (!href) return;

          masthead.clickDocumentationLink();
          cy.origin(href, () => {
            cy.get("#header").should(
              "contain.text",
              "Server Administration Guide",
            );
          });
        });
    });

    it("Enable/disable help mode in desktop mode", () => {
      masthead.assertIsDesktopView();
      cy.findByTestId("infoTab").click();
      cy.get(helpLabel).should("exist");
      masthead.toggleGlobalHelp();
      masthead.clickGlobalHelp();
      cy.get(helpLabel).should("not.exist");
      masthead.toggleGlobalHelp();
      cy.get(helpLabel).should("exist");
    });
  });

  describe("Mobile view", () => {
    it("Mobile menu is shown when in mobile view", () => {
      cy.viewport("samsung-s10");
      masthead.assertIsMobileView();
    });

    it("Enable/disable help mode in mobile view", () => {
      cy.viewport("samsung-s10");
      masthead
        .assertIsMobileView()
        .toggleUsernameDropdown()
        .toggleMobileViewHelp();
      masthead.toggleMobileViewHelp();
      cy.findByTestId("helpIcon").should("exist");
    });
  });

  describe("Accessibility tests for masthead", () => {
    beforeEach(() => {
      loginPage.logIn();
      keycloakBefore();
      sidebarPage.waitForPageLoad();
      masthead.accountManagement();
      cy.injectAxe();
    });

    it("Check a11y violations on load/ masthead", () => {
      cy.checkA11y();
    });
  });
});
