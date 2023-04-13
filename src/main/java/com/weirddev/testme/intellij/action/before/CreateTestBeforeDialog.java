// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.weirddev.testme.intellij.action.before;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CreateTestBeforeDialog extends DialogWrapper {
    private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";
    private static final String SHOW_INHERITED_MEMBERS_PROPERTY = CreateTestBeforeDialog.class.getName() + ".includeInheritedMembers";

    private final Project myProject;
    private final PsiClass myTargetClass;
    private final PsiPackage myTargetPackage;


    private EditorTextField myTargetClassNameField;
    private final JCheckBox myShowInheritedMethodsBox = new JCheckBox(JavaBundle.message("intention.create.test.dialog.show.inherited"));

    private final JCheckBox myAllMethodsBox = new JCheckBox("");
    private final MemberSelectionTable myMethodsTable = new MemberSelectionTable(Collections.emptyList(), null);


    public CreateTestBeforeDialog(@NotNull Project project,
                                  @NotNull @NlsContexts.DialogTitle String title,
                                  PsiClass targetClass,
                                  PsiPackage targetPackage,
                                  Module targetModule) {
        super(project, true);
        myProject = project;

        myTargetClass = targetClass;
        myTargetPackage = targetPackage;

        setTitle(title);
        init();
    }

    protected String suggestTestClassName(PsiClass targetClass) {
        JavaCodeStyleSettings customSettings = JavaCodeStyleSettings.getInstance(targetClass.getContainingFile());
        String prefix = customSettings.TEST_NAME_PREFIX;
        String suffix = customSettings.TEST_NAME_SUFFIX;
        return prefix + targetClass.getName() + suffix;
    }


    private void updateMethodsTable() {
        List<MemberInfo> methods = TestIntegrationUtils.extractClassMethods(
                myTargetClass, myShowInheritedMethodsBox.isSelected());
        for (MemberInfo each : methods) {
            each.setChecked(true);
        }
        setMemberInfos(methods);

    }

    private void setMemberInfos(List<MemberInfo> methods) {
        myMethodsTable.setMemberInfos(methods);
        fixUpdatePresentation(methods);
    }

    @SuppressWarnings("all")
    private void fixUpdatePresentation(List<MemberInfo> methods) {
        try {
            AsyncPromise asyncPromise = (AsyncPromise) FieldUtils.readField(myMethodsTable, "myCancellablePromise", true);
            try {
                if (asyncPromise.get(100, TimeUnit.MILLISECONDS) != null) {
                    return;
                }
            } catch (Exception exception) {
                if (exception instanceof TimeoutException) {

                } else {
                    throw exception;
                }
            }
            List list = new ArrayList(methods.size());

            for (MemberInfo method : methods) {
                Object obj = MethodUtils.invokeMethod(myMethodsTable, true, "calculateMemberInfoData", method);
                list.add(obj);
            }
            asyncPromise.setResult(list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void restoreShowInheritedMembersStatus() {
        myShowInheritedMethodsBox.setSelected(getProperties().getBoolean(SHOW_INHERITED_MEMBERS_PROPERTY));
    }

    private void saveShowInheritedMembersStatus() {
        getProperties().setValue(SHOW_INHERITED_MEMBERS_PROPERTY, myShowInheritedMethodsBox.isSelected());
    }

    private PropertiesComponent getProperties() {
        return PropertiesComponent.getInstance(myProject);
    }

    @Override
    protected String getDimensionServiceKey() {
        return getClass().getName();
    }

    @Override
    protected String getHelpId() {
        return "reference.dialogs.createTest";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTargetClassNameField;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints constr = new GridBagConstraints();

        constr.fill = GridBagConstraints.HORIZONTAL;
        constr.anchor = GridBagConstraints.WEST;

        int gridy = 1;


        myTargetClassNameField = new EditorTextField(suggestTestClassName(myTargetClass));
        myTargetClassNameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent e) {
                getOKAction().setEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getClassName()));
            }
        });


        constr.gridx = 1;
        constr.weightx = 1;


        constr.insets = insets(6);
        constr.gridy = gridy++;
        constr.gridx = 0;
        constr.weightx = 0;
        final JLabel membersLabel = new JLabel(JavaBundle.message("intention.create.test.dialog.select.methods"));
        membersLabel.setLabelFor(myMethodsTable);
        panel.add(membersLabel, constr);

        constr.gridx = 1;
        constr.weightx = 1;
        panel.add(myShowInheritedMethodsBox, constr);

        constr.insets = insets(7);
        constr.gridy = gridy++;
        constr.gridx = 0;
        constr.weightx = 0;
        final JLabel allMethodsLabel = new JLabel("All methods: ");
        panel.add(allMethodsLabel, constr);

        constr.gridx = 1;
        constr.weightx = 1;
        panel.add(myAllMethodsBox, constr);

        constr.insets = insets(1, 8);
        constr.gridy = gridy++;
        constr.gridx = 0;
        constr.gridwidth = GridBagConstraints.REMAINDER;
        constr.fill = GridBagConstraints.BOTH;
        constr.weighty = 1;
        panel.add(ScrollPaneFactory.createScrollPane(myMethodsTable), constr);


        final List<TestFramework> descriptors = new SmartList<>(TestFramework.EXTENSION_NAME.getExtensionList());
        descriptors.sort((d1, d2) -> Comparing.compare(d1.getName(), d2.getName()));


        myAllMethodsBox.addActionListener((e) -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            boolean selected = checkBox.isSelected();
            List<MemberInfo> methods = TestIntegrationUtils.extractClassMethods(
                    myTargetClass, myShowInheritedMethodsBox.isSelected());

            for (MemberInfo each : methods) {
                each.setChecked(selected);
            }

            setMemberInfos(methods);
        });
        myShowInheritedMethodsBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMethodsTable();
            }
        });

        restoreShowInheritedMembersStatus();
        updateMethodsTable();
        myAllMethodsBox.setSelected(true);
        return panel;
    }

    private static Insets insets(int top) {
        return insets(top, 0);
    }

    private static Insets insets(int top, int bottom) {
        return JBUI.insets(top, 8, bottom, 8);
    }

    public String getClassName() {
        return myTargetClassNameField.getText();
    }


    public Collection<MemberInfo> getSelectedMethods() {
        return myMethodsTable.getSelectedMemberInfos();
    }


    @Override
    protected void doOKAction() {
        saveShowInheritedMembersStatus();
        super.doOKAction();
    }


}