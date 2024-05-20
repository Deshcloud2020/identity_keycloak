import {
  Divider,
  Split,
  SplitItem,
  Switch,
  TextContent,
} from "@patternfly/react-core";
import {
  Dropdown,
  DropdownItem,
  DropdownToggle,
} from "@patternfly/react-core/deprecated";
import { ExternalLinkAltIcon, HelpIcon } from "@patternfly/react-icons";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import helpUrls from "../../help-urls";
import { useHelp } from "@keycloak/keycloak-ui-shared";

import "./help-header.css";

export const HelpHeader = () => {
  const [open, setOpen] = useState(false);
  const help = useHelp();
  const { t } = useTranslation();

  const dropdownItems = [
    <DropdownItem
      key="link"
      id="link"
      href={helpUrls.documentationUrl}
      target="_blank"
    >
      <Split>
        <SplitItem isFilled>{t("documentation")}</SplitItem>
        <SplitItem>
          <ExternalLinkAltIcon />
        </SplitItem>
      </Split>
    </DropdownItem>,
    <Divider key="divide" />,
    <DropdownItem key="enable" id="enable">
      <Split>
        <SplitItem isFilled>{t("enableHelpMode")}</SplitItem>
        <SplitItem>
          <Switch
            id="enableHelp"
            aria-label={t("enableHelp")}
            isChecked={help.enabled}
            label=""
            className="keycloak_help-header-switch"
            onChange={() => help.toggleHelp()}
          />
        </SplitItem>
      </Split>
      <TextContent className="keycloak_help-header-description">
        {t("helpToggleInfo")}
      </TextContent>
    </DropdownItem>,
  ];
  return (
    <Dropdown
      position="right"
      isPlain
      isOpen={open}
      toggle={
        <DropdownToggle
          toggleIndicator={null}
          onToggle={() => setOpen(!open)}
          aria-label="Help"
          id="help"
        >
          <HelpIcon />
        </DropdownToggle>
      }
      dropdownItems={dropdownItems}
    />
  );
};
