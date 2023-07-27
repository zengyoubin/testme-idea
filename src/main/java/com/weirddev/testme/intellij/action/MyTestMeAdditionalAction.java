package com.weirddev.testme.intellij.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.weirddev.testme.intellij.template.TemplateDescriptor;
import com.weirddev.testme.intellij.ui.popup.ConfigurationLinkAction;
import com.weirddev.testme.intellij.ui.popup.TestMePopUpHandler;
import com.weirddev.testme.intellij.utils.TestSubjectResolverUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


/**
 * Date: 10/15/2016
 *
 * @author Yaron Yamin
 */
public class MyTestMeAdditionalAction extends ConfigurationLinkAction {

    private final Editor editor;
    private final PsiFile file;
    private final MyCreateTestMeAction testMeCreator;

    public MyTestMeAdditionalAction(Editor editor, PsiFile file) {
        this.editor = editor;
        this.file = file;
        testMeCreator = new MyCreateTestMeAction();
    }

    @NotNull
    @Override
    public String getText() {
        return "Generate Test";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.General.Add;// Icons.TEST_ME;
    }

    @Override
    public void execute(Project project) {
        if (!file.getManager().isInProject(file)) return;
        final PsiElement element = TestSubjectResolverUtils.getTestableElement(editor, file);
        if (element != null) {
            testMeCreator.invoke(project, editor, element);
        }
    }

}
