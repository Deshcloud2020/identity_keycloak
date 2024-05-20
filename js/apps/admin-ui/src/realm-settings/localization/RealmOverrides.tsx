import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import {
  AlertVariant,
  Button,
  ButtonVariant,
  Divider,
  Form,
  FormGroup,
  Text,
  TextContent,
  TextInput,
  TextVariants,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  Dropdown,
  DropdownItem,
  KebabToggle,
  Select,
  SelectGroup,
  SelectOption,
  SelectVariant,
} from "@patternfly/react-core/deprecated";
import {
  CheckIcon,
  PencilAltIcon,
  SearchIcon,
  TimesIcon,
} from "@patternfly/react-icons";
import {
  ActionsColumn,
  IRow,
  IRowCell,
  Table,
  Tbody,
  Td,
  Th,
  Thead,
  Tr,
} from "@patternfly/react-table";
import { cloneDeep, isEqual, uniqWith } from "lodash-es";
import { ChangeEvent, useEffect, useState, type FormEvent } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../../admin-client";
import { useAlerts } from "../../components/alert/Alerts";
import { useConfirmDialog } from "../../components/confirm-dialog/ConfirmDialog";
import { KeyValueType } from "../../components/key-value-form/key-value-convert";
import { ListEmptyState } from "../../components/list-empty-state/ListEmptyState";
import { PaginatingTableToolbar } from "../../components/table-toolbar/PaginatingTableToolbar";
import { useRealm } from "../../context/realm-context/RealmContext";
import { useWhoAmI } from "../../context/whoami/WhoAmI";
import { DEFAULT_LOCALE } from "../../i18n/i18n";
import { localeToDisplayName } from "../../util";
import { AddTranslationModal } from "../AddTranslationModal";

type RealmOverridesProps = {
  internationalizationEnabled: boolean;
  watchSupportedLocales: string[];
  realm: RealmRepresentation;
  tableData: Record<string, string>[] | undefined;
};

type EditStatesType = { [key: number]: boolean };

export type TranslationForm = {
  key: string;
  value: string;
  translation: KeyValueType;
};

export enum RowEditAction {
  Save = "save",
  Cancel = "cancel",
  Edit = "edit",
  Delete = "delete",
}

export const RealmOverrides = ({
  internationalizationEnabled,
  watchSupportedLocales,
  realm,
  tableData,
}: RealmOverridesProps) => {
  const { adminClient } = useAdminClient();

  const { t } = useTranslation();
  const [addTranslationModalOpen, setAddTranslationModalOpen] = useState(false);
  const [filterDropdownOpen, setFilterDropdownOpen] = useState(false);
  const [translations, setTranslations] = useState<[string, string][]>([]);
  const [selectMenuLocale, setSelectMenuLocale] = useState(DEFAULT_LOCALE);
  const [kebabOpen, setKebabOpen] = useState(false);
  const { getValues, handleSubmit } = useForm();
  const [selectMenuValueSelected, setSelectMenuValueSelected] = useState(false);
  const [tableRows, setTableRows] = useState<IRow[]>([]);
  const [tableKey, setTableKey] = useState(0);
  const [max, setMax] = useState(10);
  const [first, setFirst] = useState(0);
  const [filter, setFilter] = useState("");
  const translationForm = useForm<TranslationForm>({ mode: "onChange" });
  const { addAlert, addError } = useAlerts();
  const { realm: currentRealm } = useRealm();
  const { whoAmI } = useWhoAmI();
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [areAllRowsSelected, setAreAllRowsSelected] = useState(false);
  const [editStates, setEditStates] = useState<EditStatesType>({});
  const [formValue, setFormValue] = useState("");
  const refreshTable = () => {
    setTableKey(tableKey + 1);
  };

  useEffect(() => {
    const fetchLocalizationTexts = async () => {
      try {
        let result = await adminClient.realms.getRealmLocalizationTexts({
          first,
          max,
          realm: realm.realm!,
          selectedLocale:
            selectMenuLocale ||
            getValues("defaultLocale") ||
            whoAmI.getLocale(),
        });

        if (filter) {
          const searchInTranslations = (idx: number) => {
            return Object.entries(result).filter((i) =>
              i[idx].includes(filter),
            );
          };

          const filtered = uniqWith(
            searchInTranslations(0).concat(searchInTranslations(1)),
            isEqual,
          );

          result = Object.fromEntries(filtered);
        }

        return Object.entries(result).slice(first, first + max);
      } catch (error) {
        return [];
      }
    };

    fetchLocalizationTexts().then((translations) => {
      setTranslations(translations);

      const updatedRows: IRow[] = translations.map(
        (translation): IRow => ({
          rowEditBtnAriaLabel: () =>
            t("rowEditBtnAriaLabel", {
              translation: translation[1],
            }),
          rowSaveBtnAriaLabel: () =>
            t("rowSaveBtnAriaLabel", {
              translation: translation[1],
            }),
          rowCancelBtnAriaLabel: () =>
            t("rowCancelBtnAriaLabel", {
              translation: translation[1],
            }),
          cells: [
            {
              title: translation[0],
              props: {
                value: translation[0],
              },
            },
            {
              title: translation[1],
              props: {
                value: translation[1],
              },
            },
          ],
        }),
      );

      setTableRows(updatedRows);
    });
  }, [tableKey, tableData, first, max, filter]);

  const handleModalToggle = () => {
    setAddTranslationModalOpen(!addTranslationModalOpen);
  };

  const options = [
    <SelectGroup label={t("defaultLocale")} key="group1">
      <SelectOption key={DEFAULT_LOCALE} value={DEFAULT_LOCALE}>
        {localeToDisplayName(DEFAULT_LOCALE, whoAmI.getDisplayName())}
      </SelectOption>
    </SelectGroup>,
    <Divider key="divider" />,
    <SelectGroup label={t("supportedLocales")} key="group2">
      {watchSupportedLocales.map((locale) => (
        <SelectOption key={locale} value={locale}>
          {localeToDisplayName(locale, whoAmI.getLocale())}
        </SelectOption>
      ))}
    </SelectGroup>,
  ];

  const addKeyValue = async (pair: KeyValueType): Promise<void> => {
    try {
      await adminClient.realms.addLocalization(
        {
          realm: currentRealm!,
          selectedLocale:
            selectMenuLocale || getValues("defaultLocale") || DEFAULT_LOCALE,
          key: pair.key,
        },
        pair.value,
      );

      adminClient.setConfig({
        realmName: currentRealm!,
      });
      refreshTable();
      translationForm.setValue("key", "");
      translationForm.setValue("value", "");
      addAlert(t("addTranslationSuccess"), AlertVariant.success);
    } catch (error) {
      addError(t("addTranslationError"), error);
    }
  };

  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "deleteConfirmTranslationTitle",
    messageKey: t("translationDeleteConfirmDialog", {
      count: selectedRowKeys.length,
    }),
    continueButtonLabel: "delete",
    continueButtonVariant: ButtonVariant.danger,
    onCancel: () => {
      setSelectedRowKeys([]);
      setAreAllRowsSelected(false);
    },
    onConfirm: async () => {
      try {
        for (const key of selectedRowKeys) {
          await adminClient.realms.deleteRealmLocalizationTexts({
            realm: currentRealm!,
            selectedLocale: selectMenuLocale,
            key: key,
          });
        }
        setAreAllRowsSelected(false);
        setSelectedRowKeys([]);
        refreshTable();
        addAlert(t("deleteAllTranslationsSuccess"), AlertVariant.success);
      } catch (error) {
        addError("deleteAllTranslationsError", error);
      }
    },
  });

  const handleRowSelect = (
    event: ChangeEvent<HTMLInputElement>,
    rowIndex: number,
  ) => {
    const selectedKey = (tableRows[rowIndex].cells?.[0] as IRowCell).props
      .value;
    if (event.target.checked) {
      setSelectedRowKeys((prevSelected) => [...prevSelected, selectedKey]);
    } else {
      setSelectedRowKeys((prevSelected) =>
        prevSelected.filter((key) => key !== selectedKey),
      );
    }

    setAreAllRowsSelected(
      tableRows.length ===
        selectedRowKeys.length + (event.target.checked ? 1 : -1),
    );
  };

  const toggleSelectAllRows = () => {
    if (areAllRowsSelected) {
      setSelectedRowKeys([]);
    } else {
      setSelectedRowKeys(
        tableRows.map((row) => (row.cells?.[0] as IRowCell).props.value),
      );
    }
    setAreAllRowsSelected(!areAllRowsSelected);
  };

  const isRowSelected = (key: any) => {
    return selectedRowKeys.includes(key);
  };

  const onSubmit = async (inputValue: string, rowIndex: number) => {
    const newRows = cloneDeep(tableRows);

    const newRow = cloneDeep(newRows[rowIndex]);
    (newRow.cells?.[1] as IRowCell).props.value = inputValue;
    newRows[rowIndex] = newRow;

    try {
      const key = (newRow.cells?.[0] as IRowCell).props.value;
      const value = (newRow.cells?.[1] as IRowCell).props.value;

      await adminClient.realms.addLocalization(
        {
          realm: realm.realm!,
          selectedLocale:
            selectMenuLocale || getValues("defaultLocale") || DEFAULT_LOCALE,
          key,
        },
        value,
      );

      addAlert(t("updateTranslationSuccess"), AlertVariant.success);
      setTableRows(newRows);
    } catch (error) {
      addAlert(t("updateTranslationError"), AlertVariant.danger);
    }

    setEditStates((prevEditStates) => ({
      ...prevEditStates,
      [rowIndex]: false,
    }));
  };

  return (
    <>
      <DeleteConfirm />
      {addTranslationModalOpen && (
        <AddTranslationModal
          handleModalToggle={handleModalToggle}
          save={(pair: any) => {
            addKeyValue(pair);
            handleModalToggle();
          }}
          form={translationForm}
        />
      )}
      <TextContent>
        <Text
          className="pf-v5-u-mt-lg pf-v5-u-ml-md"
          component={TextVariants.p}
        >
          {t("realmOverridesDescription")}
        </Text>
      </TextContent>
      <PaginatingTableToolbar
        count={translations.length}
        first={first}
        max={max}
        onNextClick={setFirst}
        onPreviousClick={setFirst}
        onPerPageSelect={(first, max) => {
          setFirst(first);
          setMax(max);
        }}
        inputGroupName={"search"}
        inputGroupOnEnter={(search) => {
          setFilter(search);
          setFirst(0);
          setMax(10);
        }}
        inputGroupPlaceholder={t("searchForTranslation")}
        toolbarItem={
          <>
            <Button
              data-testid="add-translationBtn"
              onClick={() => {
                setAddTranslationModalOpen(true);
                setAreAllRowsSelected(false);
                setSelectedRowKeys([]);
              }}
            >
              {t("addTranslation")}
            </Button>
            <ToolbarItem>
              <Dropdown
                toggle={
                  <KebabToggle onToggle={() => setKebabOpen(!kebabOpen)} />
                }
                isOpen={kebabOpen}
                isPlain
                data-testid="toolbar-deleteBtn"
                dropdownItems={[
                  <DropdownItem
                    key="action"
                    component="button"
                    data-testid="delete-selected-TranslationBtn"
                    isDisabled={
                      translations.length === 0 || selectedRowKeys.length === 0
                    }
                    onClick={() => {
                      toggleDeleteDialog();
                      setKebabOpen(false);
                    }}
                  >
                    {t("delete")}
                  </DropdownItem>,
                ]}
              />
            </ToolbarItem>
          </>
        }
        searchTypeComponent={
          <ToolbarItem>
            <Select
              width={180}
              isOpen={filterDropdownOpen}
              className="kc-filter-by-locale-select"
              variant={SelectVariant.single}
              isDisabled={!internationalizationEnabled}
              onToggle={(_event, isExpanded) =>
                setFilterDropdownOpen(isExpanded)
              }
              onSelect={(_, value) => {
                setSelectMenuLocale(value.toString());
                setSelectMenuValueSelected(true);
                refreshTable();
                setFilterDropdownOpen(false);
              }}
              selections={
                selectMenuValueSelected
                  ? localeToDisplayName(selectMenuLocale, whoAmI.getLocale())
                  : realm.defaultLocale !== ""
                    ? localeToDisplayName(DEFAULT_LOCALE, whoAmI.getLocale())
                    : t("placeholderText")
              }
            >
              {options}
            </Select>
          </ToolbarItem>
        }
      >
        {translations.length === 0 && !filter && (
          <ListEmptyState
            hasIcon
            message={t("noTranslations")}
            instructions={t("noTranslationsInstructions")}
            onPrimaryAction={handleModalToggle}
          />
        )}
        {translations.length === 0 && filter && (
          <ListEmptyState
            hasIcon
            icon={SearchIcon}
            isSearchVariant
            message={t("noSearchResults")}
            instructions={t("noRealmOverridesSearchResultsInstructions")}
          />
        )}
        {translations.length !== 0 && (
          <Table
            aria-label={t("editableRowsTable")}
            data-testid="editable-rows-table"
          >
            <Thead>
              <Tr>
                <Th className="pf-v5-u-px-lg">
                  <input
                    type="checkbox"
                    aria-label={t("selectAll")}
                    checked={areAllRowsSelected}
                    onChange={toggleSelectAllRows}
                    data-testid="selectAll"
                  />
                </Th>
                <Th className="pf-v5-u-py-lg">{t("key")}</Th>
                <Th className="pf-v5-u-py-lg">{t("value")}</Th>
                <Th aria-hidden="true" />
              </Tr>
            </Thead>
            <Tbody>
              {tableRows.map((row, rowIndex) => (
                <Tr key={(row.cells?.[0] as IRowCell).props.value}>
                  <Td
                    className="pf-v5-u-px-lg"
                    select={{
                      rowIndex,
                      onSelect: (event) =>
                        handleRowSelect(
                          event as ChangeEvent<HTMLInputElement>,
                          rowIndex,
                        ),
                      isSelected: isRowSelected(
                        (row.cells?.[0] as IRowCell).props.value,
                      ),
                    }}
                  />
                  <Td className="pf-m-sm pf-v5-u-px-sm" dataLabel={t("key")}>
                    {(row.cells?.[0] as IRowCell).props.value}
                  </Td>
                  <Td
                    className="pf-m-sm pf-v5-u-px-sm"
                    dataLabel={t("value")}
                    key={rowIndex}
                  >
                    <Form
                      isHorizontal
                      className="kc-form-translationValue"
                      onSubmit={handleSubmit(() => {
                        onSubmit(formValue, rowIndex);
                      })}
                    >
                      <FormGroup
                        fieldId="kc-translationValue"
                        className="pf-v5-u-display-inline-block"
                      >
                        {editStates[rowIndex] ? (
                          <>
                            <TextInput
                              aria-label={t("editTranslationValue")}
                              type="text"
                              className="pf-v5-u-w-initial"
                              data-testid={`editTranslationValueInput-${rowIndex}`}
                              value={formValue}
                              onChange={(
                                event: FormEvent<HTMLInputElement>,
                                value: string,
                              ) => {
                                setFormValue(value);
                              }}
                              key={`edit-input-${rowIndex}`}
                            />
                            <Button
                              variant="link"
                              className="pf-m-plain"
                              data-testid={`editTranslationAcceptBtn-${rowIndex}`}
                              type="submit"
                              aria-label={t("acceptBtn")}
                              icon={<CheckIcon />}
                            />
                            <Button
                              variant="link"
                              className="pf-m-plain"
                              data-testid={`editTranslationCancelBtn-${rowIndex}`}
                              icon={<TimesIcon />}
                              aria-label={t("cancelBtn")}
                              onClick={() => {
                                setEditStates((prevEditStates) => ({
                                  ...prevEditStates,
                                  [rowIndex]: false,
                                }));
                              }}
                            />
                          </>
                        ) : (
                          <>
                            <span>
                              {(row.cells?.[1] as IRowCell).props.value}
                            </span>
                            <Button
                              onClick={() => {
                                const currentValue = (
                                  tableRows[rowIndex].cells?.[1] as IRowCell
                                ).props.value;
                                setFormValue(currentValue);
                                setEditStates((prevState) => ({
                                  ...prevState,
                                  [rowIndex]: true,
                                }));
                              }}
                              key={`edit-button-${rowIndex}`}
                              aria-label={t("editBtn")}
                              variant="link"
                              className="pf-m-plain"
                              data-testid={`editTranslationBtn-${rowIndex}`}
                            >
                              <PencilAltIcon />
                            </Button>
                          </>
                        )}
                      </FormGroup>
                    </Form>
                  </Td>
                  <Td isActionCell>
                    <ActionsColumn
                      items={[
                        {
                          title: t("delete"),
                          onClick: () => {
                            setSelectedRowKeys([
                              (row.cells?.[0] as IRowCell).props.value,
                            ]);
                            translations.length === 1 &&
                              setAreAllRowsSelected(true);
                            toggleDeleteDialog();
                            setKebabOpen(false);
                          },
                        },
                      ]}
                    />
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </PaginatingTableToolbar>
    </>
  );
};
