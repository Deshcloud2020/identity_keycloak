import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import { ActionGroup, Button, FormGroup } from "@patternfly/react-core";
import {
  Select,
  SelectOption,
  SelectVariant,
} from "@patternfly/react-core/deprecated";
import { useEffect, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { HelpItem, NumberControl } from "@keycloak/keycloak-ui-shared";
import { FormAccess } from "../../components/form/FormAccess";
import { convertToFormValues } from "../../util";
import { Time } from "./Time";

type BruteForceDetectionProps = {
  realm: RealmRepresentation;
  save: (realm: RealmRepresentation) => void;
};

export const BruteForceDetection = ({
  realm,
  save,
}: BruteForceDetectionProps) => {
  const { t } = useTranslation();
  const form = useForm();
  const {
    setValue,
    handleSubmit,
    formState: { isDirty },
  } = form;

  const [isBruteForceModeOpen, setIsBruteForceModeOpen] = useState(false);
  const [isBruteForceModeUpdated, setIsBruteForceModeUpdated] = useState(false);

  enum BruteForceMode {
    Disabled = "Disabled",
    PermanentLockout = "PermanentLockout",
    TemporaryLockout = "TemporaryLockout",
    PermanentAfterTemporaryLockout = "PermanentAfterTemporaryLockout",
  }

  const bruteForceModes = [
    BruteForceMode.Disabled,
    BruteForceMode.PermanentLockout,
    BruteForceMode.TemporaryLockout,
    BruteForceMode.PermanentAfterTemporaryLockout,
  ];

  const setupForm = () => {
    convertToFormValues(realm, setValue);
    setIsBruteForceModeUpdated(false);
  };
  useEffect(setupForm, []);

  const bruteForceMode = (() => {
    if (!form.getValues("bruteForceProtected")) {
      return BruteForceMode.Disabled;
    }
    if (!form.getValues("permanentLockout")) {
      return BruteForceMode.TemporaryLockout;
    }
    return form.getValues("maxTemporaryLockouts") == 0
      ? BruteForceMode.PermanentLockout
      : BruteForceMode.PermanentAfterTemporaryLockout;
  })();

  return (
    <FormProvider {...form}>
      <FormAccess
        role="manage-realm"
        isHorizontal
        onSubmit={handleSubmit(save)}
      >
        <FormGroup
          label={t("bruteForceMode")}
          fieldId="kc-brute-force-mode"
          labelIcon={
            <HelpItem
              helpText={t("bruteForceModeHelpText")}
              fieldLabelId="bruteForceMode"
            />
          }
        >
          <Select
            toggleId="kc-brute-force-mode"
            onToggle={() => setIsBruteForceModeOpen(!isBruteForceModeOpen)}
            onSelect={(_, value) => {
              switch (value as BruteForceMode) {
                case BruteForceMode.Disabled:
                  form.setValue("bruteForceProtected", false);
                  form.setValue("permanentLockout", false);
                  form.setValue("maxTemporaryLockouts", 0);
                  break;
                case BruteForceMode.TemporaryLockout:
                  form.setValue("bruteForceProtected", true);
                  form.setValue("permanentLockout", false);
                  form.setValue("maxTemporaryLockouts", 0);
                  break;
                case BruteForceMode.PermanentLockout:
                  form.setValue("bruteForceProtected", true);
                  form.setValue("permanentLockout", true);
                  form.setValue("maxTemporaryLockouts", 0);
                  break;
                case BruteForceMode.PermanentAfterTemporaryLockout:
                  form.setValue("bruteForceProtected", true);
                  form.setValue("permanentLockout", true);
                  form.setValue("maxTemporaryLockouts", 1);
                  break;
              }
              setIsBruteForceModeUpdated(true);
              setIsBruteForceModeOpen(false);
            }}
            selections={bruteForceMode}
            variant={SelectVariant.single}
            isOpen={isBruteForceModeOpen}
            data-testid="select-brute-force-mode"
            aria-label={t("selectUnmanagedAttributePolicy")}
          >
            {bruteForceModes.map((mode) => (
              <SelectOption key={mode} value={mode}>
                {t(`bruteForceMode.${mode}`)}
              </SelectOption>
            ))}
          </Select>
        </FormGroup>
        {bruteForceMode !== BruteForceMode.Disabled && (
          <>
            <NumberControl
              name="failureFactor"
              label={t("failureFactor")}
              labelIcon={t("failureFactorHelp")}
              controller={{
                defaultValue: 0,
                rules: { required: t("required") },
              }}
            />
            {bruteForceMode ===
              BruteForceMode.PermanentAfterTemporaryLockout && (
              <NumberControl
                name="maxTemporaryLockouts"
                label={t("maxTemporaryLockouts")}
                labelIcon={t("maxTemporaryLockoutsHelp")}
                controller={{
                  defaultValue: 0,
                }}
              />
            )}
            {(bruteForceMode === BruteForceMode.TemporaryLockout ||
              bruteForceMode ===
                BruteForceMode.PermanentAfterTemporaryLockout) && (
              <>
                <Time name="waitIncrementSeconds" />
                <Time name="maxFailureWaitSeconds" />
                <Time name="maxDeltaTimeSeconds" />
              </>
            )}
            <NumberControl
              name="quickLoginCheckMilliSeconds"
              label={t("quickLoginCheckMilliSeconds")}
              labelIcon={t("quickLoginCheckMilliSecondsHelp")}
              controller={{
                defaultValue: 0,
              }}
            />
            <Time name="minimumQuickLoginWaitSeconds" />
          </>
        )}

        <ActionGroup>
          <Button
            variant="primary"
            type="submit"
            data-testid="brute-force-tab-save"
            isDisabled={!isDirty && !isBruteForceModeUpdated}
          >
            {t("save")}
          </Button>
          <Button variant="link" onClick={setupForm}>
            {t("revert")}
          </Button>
        </ActionGroup>
      </FormAccess>
    </FormProvider>
  );
};
