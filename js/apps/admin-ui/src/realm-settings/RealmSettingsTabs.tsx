import { fetchWithError } from "@keycloak/keycloak-admin-client";
import { UserProfileConfig } from "@keycloak/keycloak-admin-client/lib/defs/userProfileMetadata";
import { AdminEnvironment, useEnvironment } from "@keycloak/keycloak-ui-shared";
import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import {
  AlertVariant,
  ButtonVariant,
  PageSection,
  Tab,
  TabTitleText,
  Tooltip,
} from "@patternfly/react-core";
import {
  DropdownItem,
  DropdownSeparator,
} from "@patternfly/react-core/deprecated";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useAlerts } from "../components/alert/Alerts";
import { useConfirmDialog } from "../components/confirm-dialog/ConfirmDialog";
import type { KeyValueType } from "../components/key-value-form/key-value-convert";
import {
  RoutableTabs,
  useRoutableTab,
} from "../components/routable-tabs/RoutableTabs";
import { ViewHeader } from "../components/view-header/ViewHeader";
import { useRealms } from "../context/RealmsContext";
import { useRealm } from "../context/realm-context/RealmContext";
import { toDashboard } from "../dashboard/routes/Dashboard";
import helpUrls from "../help-urls";
import { convertFormValuesToObject, convertToFormValues } from "../util";
import { getAuthorizationHeaders } from "../utils/getAuthorizationHeaders";
import { joinPath } from "../utils/joinPath";
import useIsFeatureEnabled, { Feature } from "../utils/useIsFeatureEnabled";
import { RealmSettingsEmailTab } from "./EmailTab";
import { RealmSettingsGeneralTab } from "./GeneralTab";
import { LocalizationTab } from "./localization/LocalizationTab";
import { RealmSettingsLoginTab } from "./LoginTab";
import { PartialExportDialog } from "./PartialExport";
import { PartialImportDialog } from "./PartialImport";
import { PoliciesTab } from "./PoliciesTab";
import ProfilesTab from "./ProfilesTab";
import { RealmSettingsSessionsTab } from "./SessionsTab";
import { RealmSettingsThemesTab } from "./ThemesTab";
import { RealmSettingsTokensTab } from "./TokensTab";
import { UserRegistration } from "./UserRegistration";
import { EventsTab } from "./event-config/EventsTab";
import { KeysTab } from "./keys/KeysTab";
import { ClientPoliciesTab, toClientPolicies } from "./routes/ClientPolicies";
import { RealmSettingsTab, toRealmSettings } from "./routes/RealmSettings";
import { SecurityDefenses } from "./security-defences/SecurityDefenses";
import { UserProfileTab } from "./user-profile/UserProfileTab";
import useLocale from "../utils/useLocale";
import { useAdminClient } from "../admin-client";
import { useAccess } from "../context/access/Access";

export interface UIRealmRepresentation extends RealmRepresentation {
  upConfig?: UserProfileConfig;
}

type RealmSettingsHeaderProps = {
  onChange: (value: boolean) => void;
  value: boolean;
  save: () => void;
  realmName: string;
  refresh: () => void;
};

const RealmSettingsHeader = ({
  save,
  onChange,
  value,
  realmName,
  refresh,
}: RealmSettingsHeaderProps) => {
  const { adminClient } = useAdminClient();
  const { environment } = useEnvironment<AdminEnvironment>();
  const { t } = useTranslation();
  const { refresh: refreshRealms } = useRealms();
  const { addAlert, addError } = useAlerts();
  const navigate = useNavigate();
  const [partialImportOpen, setPartialImportOpen] = useState(false);
  const [partialExportOpen, setPartialExportOpen] = useState(false);
  const { hasAccess } = useAccess();
  const canManageRealm = hasAccess("manage-realm");

  const [toggleDisableDialog, DisableConfirm] = useConfirmDialog({
    titleKey: "disableConfirmTitle",
    messageKey: "disableConfirmRealm",
    continueButtonLabel: "disable",
    onConfirm: () => {
      onChange(!value);
      save();
    },
  });

  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "deleteConfirmTitle",
    messageKey: "deleteConfirmRealmSetting",
    continueButtonLabel: "delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.realms.del({ realm: realmName });
        addAlert(t("deletedSuccessRealmSetting"), AlertVariant.success);
        await refreshRealms();
        navigate(toDashboard({ realm: environment.masterRealm }));
        refresh();
      } catch (error) {
        addError("deleteErrorRealmSetting", error);
      }
    },
  });

  return (
    <>
      <DisableConfirm />
      <DeleteConfirm />
      <PartialImportDialog
        open={partialImportOpen}
        toggleDialog={() => setPartialImportOpen(!partialImportOpen)}
      />
      <PartialExportDialog
        isOpen={partialExportOpen}
        onClose={() => setPartialExportOpen(false)}
      />
      <ViewHeader
        titleKey={realmName}
        subKey="realmSettingsExplain"
        helpUrl={helpUrls.realmSettingsUrl}
        divider={false}
        dropdownItems={[
          <DropdownItem
            key="import"
            data-testid="openPartialImportModal"
            isDisabled={!canManageRealm}
            onClick={() => {
              setPartialImportOpen(true);
            }}
          >
            {t("partialImport")}
          </DropdownItem>,
          <DropdownItem
            key="export"
            data-testid="openPartialExportModal"
            isDisabled={!canManageRealm}
            onClick={() => setPartialExportOpen(true)}
          >
            {t("partialExport")}
          </DropdownItem>,
          <DropdownSeparator key="separator" />,
          <DropdownItem
            key="delete"
            isDisabled={!canManageRealm}
            onClick={toggleDeleteDialog}
          >
            {t("delete")}
          </DropdownItem>,
        ]}
        isEnabled={value}
        isReadOnly={!canManageRealm}
        onToggle={(value) => {
          if (!value) {
            toggleDisableDialog();
          } else {
            onChange(value);
            save();
          }
        }}
      />
    </>
  );
};

type RealmSettingsTabsProps = {
  realm: UIRealmRepresentation;
  refresh: () => void;
  tableData?: Record<string, string>[];
};

export const RealmSettingsTabs = ({
  realm,
  refresh,
}: RealmSettingsTabsProps) => {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const { addAlert, addError } = useAlerts();
  const { realm: realmName } = useRealm();
  const { refresh: refreshRealms } = useRealms();
  const combinedLocales = useLocale();
  const navigate = useNavigate();
  const isFeatureEnabled = useIsFeatureEnabled();
  const [tableData, setTableData] = useState<
    Record<string, string>[] | undefined
  >(undefined);
  const { control, setValue, getValues } = useForm({
    mode: "onChange",
  });
  const [key, setKey] = useState(0);
  const refreshHeader = () => {
    setKey(key + 1);
  };

  const setupForm = (r: RealmRepresentation = realm) => {
    convertToFormValues(r, setValue);
  };

  useEffect(() => {
    setupForm();
    const fetchLocalizationTexts = async () => {
      try {
        await Promise.all(
          combinedLocales.map(async (locale) => {
            try {
              const response =
                await adminClient.realms.getRealmLocalizationTexts({
                  realm: realmName,
                  selectedLocale: locale,
                });

              if (response) {
                setTableData([response]);
              }
            } catch (error) {
              return [];
            }
          }),
        );
      } catch (error) {
        return [];
      }
    };
    fetchLocalizationTexts();
  }, [setValue, realm]);

  const save = async (r: UIRealmRepresentation) => {
    r = convertFormValuesToObject(r);
    if (
      r.attributes?.["acr.loa.map"] &&
      typeof r.attributes["acr.loa.map"] !== "string"
    ) {
      r.attributes["acr.loa.map"] = JSON.stringify(
        Object.fromEntries(
          (r.attributes["acr.loa.map"] as KeyValueType[])
            .filter(({ key }) => key !== "")
            .map(({ key, value }) => [key, value]),
        ),
      );
    }

    try {
      const savedRealm: UIRealmRepresentation = {
        ...realm,
        ...r,
        id: r.realm,
      };

      // For the default value, null is expected instead of an empty string.
      if (savedRealm.smtpServer?.port === "") {
        savedRealm.smtpServer = { ...savedRealm.smtpServer, port: null };
      }
      const response = await fetchWithError(
        joinPath(adminClient.baseUrl, `admin/realms/${realmName}/ui-ext`),
        {
          method: "PUT",
          body: JSON.stringify(savedRealm),
          headers: {
            "Content-Type": "application/json",
            ...getAuthorizationHeaders(await adminClient.getAccessToken()),
          },
        },
      );
      if (!response.ok) throw new Error(response.statusText);
      addAlert(t("realmSaveSuccess"), AlertVariant.success);
    } catch (error) {
      addError("realmSaveError", error);
    }

    const isRealmRenamed = realmName !== (r.realm || realm.realm);
    if (isRealmRenamed) {
      await refreshRealms();
      navigate(toRealmSettings({ realm: r.realm!, tab: "general" }));
    }
    refresh();
  };

  const useTab = (tab: RealmSettingsTab) =>
    useRoutableTab(toRealmSettings({ realm: realmName, tab }));

  const generalTab = useTab("general");
  const loginTab = useTab("login");
  const emailTab = useTab("email");
  const themesTab = useTab("themes");
  const keysTab = useTab("keys");
  const eventsTab = useTab("events");
  const localizationTab = useTab("localization");
  const securityDefensesTab = useTab("security-defenses");
  const sessionsTab = useTab("sessions");
  const tokensTab = useTab("tokens");
  const clientPoliciesTab = useTab("client-policies");
  const userProfileTab = useTab("user-profile");
  const userRegistrationTab = useTab("user-registration");

  const useClientPoliciesTab = (tab: ClientPoliciesTab) =>
    useRoutableTab(
      toClientPolicies({
        realm: realmName,
        tab,
      }),
    );

  const clientPoliciesProfilesTab = useClientPoliciesTab("profiles");
  const clientPoliciesPoliciesTab = useClientPoliciesTab("policies");

  return (
    <>
      <Controller
        name="enabled"
        defaultValue={true}
        control={control}
        render={({ field }) => (
          <RealmSettingsHeader
            value={field.value}
            onChange={field.onChange}
            realmName={realmName}
            refresh={refreshHeader}
            save={() => save(getValues())}
          />
        )}
      />
      <PageSection variant="light" className="pf-v5-u-p-0">
        <RoutableTabs
          isBox
          mountOnEnter
          aria-label="realm-settings-tabs"
          defaultLocation={toRealmSettings({
            realm: realmName,
            tab: "general",
          })}
        >
          <Tab
            title={<TabTitleText>{t("general")}</TabTitleText>}
            data-testid="rs-general-tab"
            {...generalTab}
          >
            <RealmSettingsGeneralTab realm={realm} save={save} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("login")}</TabTitleText>}
            data-testid="rs-login-tab"
            {...loginTab}
          >
            <RealmSettingsLoginTab refresh={refresh} realm={realm} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("email")}</TabTitleText>}
            data-testid="rs-email-tab"
            {...emailTab}
          >
            <RealmSettingsEmailTab realm={realm} save={save} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("themes")}</TabTitleText>}
            data-testid="rs-themes-tab"
            {...themesTab}
          >
            <RealmSettingsThemesTab realm={realm} save={save} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("keys")}</TabTitleText>}
            data-testid="rs-keys-tab"
            {...keysTab}
          >
            <KeysTab />
          </Tab>
          <Tab
            title={<TabTitleText>{t("events")}</TabTitleText>}
            data-testid="rs-realm-events-tab"
            {...eventsTab}
          >
            <EventsTab realm={realm} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("localization")}</TabTitleText>}
            data-testid="rs-localization-tab"
            {...localizationTab}
          >
            <LocalizationTab
              key={key}
              save={save}
              realm={realm}
              tableData={tableData}
            />
          </Tab>
          <Tab
            title={<TabTitleText>{t("securityDefences")}</TabTitleText>}
            data-testid="rs-security-defenses-tab"
            {...securityDefensesTab}
          >
            <SecurityDefenses realm={realm} save={save} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("sessions")}</TabTitleText>}
            data-testid="rs-sessions-tab"
            {...sessionsTab}
          >
            <RealmSettingsSessionsTab key={key} realm={realm} save={save} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("tokens")}</TabTitleText>}
            data-testid="rs-tokens-tab"
            {...tokensTab}
          >
            <RealmSettingsTokensTab save={save} realm={realm} />
          </Tab>
          {isFeatureEnabled(Feature.ClientPolicies) && (
            <Tab
              title={<TabTitleText>{t("clientPolicies")}</TabTitleText>}
              data-testid="rs-clientPolicies-tab"
              {...clientPoliciesTab}
            >
              <RoutableTabs
                mountOnEnter
                defaultLocation={toClientPolicies({
                  realm: realmName,
                  tab: "profiles",
                })}
              >
                <Tab
                  id="profiles"
                  data-testid="rs-policies-clientProfiles-tab"
                  aria-label={t("clientProfilesSubTab")}
                  title={<TabTitleText>{t("profiles")}</TabTitleText>}
                  tooltip={
                    <Tooltip content={t("clientPoliciesProfilesHelpText")} />
                  }
                  {...clientPoliciesProfilesTab}
                >
                  <ProfilesTab />
                </Tab>
                <Tab
                  id="policies"
                  data-testid="rs-policies-clientPolicies-tab"
                  aria-label={t("clientPoliciesSubTab")}
                  {...clientPoliciesPoliciesTab}
                  title={<TabTitleText>{t("policies")}</TabTitleText>}
                  tooltip={
                    <Tooltip content={t("clientPoliciesPoliciesHelpText")} />
                  }
                >
                  <PoliciesTab />
                </Tab>
              </RoutableTabs>
            </Tab>
          )}
          <Tab
            title={<TabTitleText>{t("userProfile")}</TabTitleText>}
            data-testid="rs-user-profile-tab"
            {...userProfileTab}
          >
            <UserProfileTab setTableData={setTableData as any} />
          </Tab>
          <Tab
            title={<TabTitleText>{t("userRegistration")}</TabTitleText>}
            data-testid="rs-userRegistration-tab"
            {...userRegistrationTab}
          >
            <UserRegistration />
          </Tab>
        </RoutableTabs>
      </PageSection>
    </>
  );
};
