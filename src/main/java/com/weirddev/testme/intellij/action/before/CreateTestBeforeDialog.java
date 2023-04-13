// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.weirddev.testme.intellij.action.before;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CreateTestBeforeDialog extends DialogWrapper {
    private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";
    private static final String SHOW_INHERITED_MEMBERS_PROPERTY = CreateTestBeforeDialog.class.getName() + ".includeInheritedMembers";

    private final Project myProject;
    private final PsiClass myTargetClass;
    private final PsiPackage myTargetPackage;


    private EditorTextField myTargetClassNameField;
    private ReferenceEditorComboWithBrowseButton myTargetPackageField;
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


        String targetPackageName = myTargetPackage != null ? myTargetPackage.getQualifiedName() : "";
        myTargetPackageField = new PackageNameReferenceEditorCombo(targetPackageName, myProject, RECENTS_KEY, JavaBundle.message("dialog.create.class.package.chooser.title"));

        new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                myTargetPackageField.getButton().doClick();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                myTargetPackageField.getChildComponent());
        JPanel targetPackagePanel = new JPanel(new BorderLayout());
        targetPackagePanel.add(myTargetPackageField, BorderLayout.CENTER);


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
        RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myTargetPackageField.getText());

        saveShowInheritedMembersStatus();
        super.doOKAction();
    }


}