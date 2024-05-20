import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import ComponentTypeRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentTypeRepresentation";
import RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import { ActionGroup, Button, Form, PageSection } from "@patternfly/react-core";
import { useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { useAdminClient } from "../admin-client";
import { useAlerts } from "../components/alert/Alerts";
import { DynamicComponents } from "../components/dynamic/DynamicComponents";
import { useRealm } from "../context/realm-context/RealmContext";
import { useFetch } from "../utils/useFetch";
import { useParams } from "../utils/useParams";
import { PAGE_PROVIDER, TAB_PROVIDER } from "./PageList";
import { toPage } from "./routes";

type PageHandlerProps = {
  id?: string;
  providerType: typeof TAB_PROVIDER | typeof PAGE_PROVIDER;
  page: ComponentTypeRepresentation;
};

export const PageHandler = ({
  id: idAttribute,
  providerType,
  page: { id: providerId, ...page },
}: PageHandlerProps) => {
  const { adminClient } = useAdminClient();

  const { t } = useTranslation();
  const form = useForm<ComponentTypeRepresentation>();
  const { realm: realmName } = useRealm();
  const [realm, setRealm] = useState<RealmRepresentation>();
  const { addAlert, addError } = useAlerts();
  const [id, setId] = useState(idAttribute);
  const params = useParams();

  useFetch(
    async () =>
      await Promise.all([
        adminClient.realms.findOne({ realm: realmName }),
        id ? adminClient.components.findOne({ id }) : Promise.resolve(),
        providerType === TAB_PROVIDER
          ? adminClient.components.find({ type: TAB_PROVIDER })
          : Promise.resolve(),
      ]),
    ([realm, data, tabs]) => {
      setRealm(realm);
      const tab = (tabs || []).find((t) => t.providerId === providerId);
      form.reset(data || tab || {});
      if (tab) setId(tab.id);
    },
    [],
  );

  const onSubmit = async (component: ComponentRepresentation) => {
    if (component.config || params) {
      component.config = Object.assign(component.config || {}, params);
      Object.entries(component.config).forEach(
        ([key, value]) =>
          (component.config![key] = Array.isArray(value) ? value : [value]),
      );
    }
    try {
      const updatedComponent = {
        ...component,
        providerId,
        providerType,
        parentId: realm?.id,
      };
      if (id) {
        await adminClient.components.update({ id }, updatedComponent);
      } else {
        await adminClient.components.create(updatedComponent);
      }
      addAlert("itemSaveSuccessful");
    } catch (error) {
      addError("itemSaveError", error);
    }
  };

  return (
    <PageSection variant="light">
      <Form
        isHorizontal
        onSubmit={form.handleSubmit(onSubmit)}
        className="keycloak__form"
      >
        <FormProvider {...form}>
          <DynamicComponents properties={page.properties} />
        </FormProvider>

        <ActionGroup>
          <Button data-testid="save" type="submit">
            {t("save")}
          </Button>
          <Button
            variant="link"
            component={(props) => (
              <Link
                {...props}
                to={toPage({ realm: realmName, providerId: providerId! })}
              />
            )}
          >
            {t("cancel")}
          </Button>
        </ActionGroup>
      </Form>
    </PageSection>
  );
};
