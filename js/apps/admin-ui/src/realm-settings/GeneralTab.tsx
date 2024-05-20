import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import {
  UnmanagedAttributePolicy,
  UserProfileConfig,
} from "@keycloak/keycloak-admin-client/lib/defs/userProfileMetadata";
import {
  ActionGroup,
  Button,
  ClipboardCopy,
  FormGroup,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { useEffect, useState } from "react";
import { Controller, FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import {
  FormErrorText,
  HelpItem,
  SelectControl,
  TextControl,
} from "@keycloak/keycloak-ui-shared";
import { useAdminClient } from "../admin-client";
import { DefaultSwitchControl } from "../components/SwitchControl";
import { FormattedLink } from "../components/external-link/FormattedLink";
import { FormAccess } from "../components/form/FormAccess";
import { KeyValueInput } from "../components/key-value-form/KeyValueInput";
import { KeycloakSpinner } from "../components/keycloak-spinner/KeycloakSpinner";
import { useRealm } from "../context/realm-context/RealmContext";
import {
  addTrailingSlash,
  convertAttributeNameToForm,
  convertToFormValues,
} from "../util";
import { useFetch } from "../utils/useFetch";
import { UIRealmRepresentation } from "./RealmSettingsTabs";

type RealmSettingsGeneralTabProps = {
  realm: UIRealmRepresentation;
  save: (realm: UIRealmRepresentation) => void;
};

export const RealmSettingsGeneralTab = ({
  realm,
  save,
}: RealmSettingsGeneralTabProps) => {
  const { adminClient } = useAdminClient();

  const { realm: realmName } = useRealm();
  const [userProfileConfig, setUserProfileConfig] =
    useState<UserProfileConfig>();

  useFetch(
    () => adminClient.users.getProfile({ realm: realmName }),
    (config) => setUserProfileConfig(config),
    [],
  );

  if (!userProfileConfig) {
    return <KeycloakSpinner />;
  }

  return (
    <RealmSettingsGeneralTabForm
      realm={realm}
      save={save}
      userProfileConfig={userProfileConfig}
    />
  );
};

type RealmSettingsGeneralTabFormProps = {
  realm: UIRealmRepresentation;
  save: (realm: UIRealmRepresentation) => void;
  userProfileConfig: UserProfileConfig;
};

type FormFields = Omit<RealmRepresentation, "groups"> & {
  unmanagedAttributePolicy: UnmanagedAttributePolicy;
};

const REQUIRE_SSL_TYPES = ["all", "external", "none"];

const UNMANAGED_ATTRIBUTE_POLICIES = [
  UnmanagedAttributePolicy.Disabled,
  UnmanagedAttributePolicy.Enabled,
  UnmanagedAttributePolicy.AdminView,
  UnmanagedAttributePolicy.AdminEdit,
];

function RealmSettingsGeneralTabForm({
  realm,
  save,
  userProfileConfig,
}: RealmSettingsGeneralTabFormProps) {
  const { adminClient } = useAdminClient();

  const { t } = useTranslation();
  const { realm: realmName } = useRealm();
  const form = useForm<FormFields>();
  const {
    control,
    handleSubmit,
    setValue,
    formState: { isDirty, errors },
  } = form;

  const setupForm = () => {
    convertToFormValues(realm, setValue);
    if (realm.attributes?.["acr.loa.map"]) {
      const result = Object.entries(
        JSON.parse(realm.attributes["acr.loa.map"]),
      ).flatMap(([key, value]) => ({ key, value }));
      result.concat({ key: "", value: "" });
      setValue(
        convertAttributeNameToForm("attributes.acr.loa.map") as any,
        result,
      );
    }
  };

  useEffect(setupForm, []);

  const onSubmit = handleSubmit(({ unmanagedAttributePolicy, ...data }) => {
    const upConfig = { ...userProfileConfig };

    if (unmanagedAttributePolicy === UnmanagedAttributePolicy.Disabled) {
      delete upConfig.unmanagedAttributePolicy;
    } else {
      upConfig.unmanagedAttributePolicy = unmanagedAttributePolicy;
    }

    save({ ...data, upConfig });
  });

  return (
    <PageSection variant="light">
      <FormProvider {...form}>
        <FormAccess
          isHorizontal
          role="manage-realm"
          className="pf-u-mt-lg"
          onSubmit={onSubmit}
        >
          <FormGroup label={t("realmId")} fieldId="kc-realm-id" isRequired>
            <Controller
              name="realm"
              control={control}
              rules={{
                required: { value: true, message: t("required") },
              }}
              defaultValue=""
              render={({ field }) => (
                <ClipboardCopy
                  data-testid="realmName"
                  onChange={field.onChange}
                >
                  {field.value}
                </ClipboardCopy>
              )}
            />
            {errors.realm && (
              <FormErrorText
                data-testid="realm-id-error"
                message={errors.realm.message as string}
              />
            )}
          </FormGroup>
          <TextControl name="displayName" label={t("displayName")} />
          <TextControl name="displayNameHtml" label={t("htmlDisplayName")} />
          <TextControl
            name={convertAttributeNameToForm("attributes.frontendUrl")}
            type="url"
            label={t("htmlDisplayName")}
            labelIcon={t("frontendUrlHelp")}
          />
          <SelectControl
            name="sslRequired"
            label={t("requireSsl")}
            labelIcon={t("requireSslHelp")}
            controller={{
              defaultValue: "none",
            }}
            options={REQUIRE_SSL_TYPES.map((sslType) => ({
              key: sslType,
              value: t(`sslType.${sslType}`),
            }))}
          />
          <FormGroup
            label={t("acrToLoAMapping")}
            fieldId="acrToLoAMapping"
            labelIcon={
              <HelpItem
                helpText={t("acrToLoAMappingHelp")}
                fieldLabelId="acrToLoAMapping"
              />
            }
          >
            <KeyValueInput
              label={t("acrToLoAMapping")}
              name={convertAttributeNameToForm("attributes.acr.loa.map")}
            />
          </FormGroup>
          <DefaultSwitchControl
            name="userManagedAccessAllowed"
            label={t("userManagedAccess")}
            labelIcon={t("userManagedAccessHelp")}
          />
          <SelectControl
            name="unmanagedAttributePolicy"
            label={t("unmanagedAttributes")}
            labelIcon={t("unmanagedAttributesHelpText")}
            controller={{
              defaultValue: UNMANAGED_ATTRIBUTE_POLICIES[0],
            }}
            options={UNMANAGED_ATTRIBUTE_POLICIES.map((policy) => ({
              key: policy,
              value: t(`unmanagedAttributePolicy.${policy}`),
            }))}
          />
          <FormGroup
            label={t("endpoints")}
            labelIcon={
              <HelpItem
                helpText={t("endpointsHelp")}
                fieldLabelId="endpoints"
              />
            }
            fieldId="kc-endpoints"
          >
            <Stack>
              <StackItem>
                <FormattedLink
                  href={`${addTrailingSlash(
                    adminClient.baseUrl,
                  )}realms/${realmName}/.well-known/openid-configuration`}
                  title={t("openIDEndpointConfiguration")}
                />
              </StackItem>
              <StackItem>
                <FormattedLink
                  href={`${addTrailingSlash(
                    adminClient.baseUrl,
                  )}realms/${realmName}/protocol/saml/descriptor`}
                  title={t("samlIdentityProviderMetadata")}
                />
              </StackItem>
            </Stack>
          </FormGroup>
          <ActionGroup>
            <Button
              variant="primary"
              type="submit"
              data-testid="general-tab-save"
              isDisabled={!isDirty}
            >
              {t("save")}
            </Button>
            <Button
              data-testid="general-tab-revert"
              variant="link"
              onClick={setupForm}
            >
              {t("revert")}
            </Button>
          </ActionGroup>
        </FormAccess>
      </FormProvider>
    </PageSection>
  );
}
