package com.hz.yk;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;

import java.util.List;

/**
 * @author wuzheng.yk
 * @date 2021/5/12
 */
public class GenerateAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        PsiMethod psiMethod = getPsiMethodFromContext(event);
        generateO2OMethod(psiMethod);
    }

    /**
     * 启动写线程
     *
     * @param psiMethod
     */
    private void generateO2OMethod(final PsiMethod psiMethod) {
        WriteCommandAction.runWriteCommandAction(psiMethod.getProject(), () -> {
            createO2O(psiMethod);
        });
    }

    private void createO2O(PsiMethod psiMethod) {
        String methodName = psiMethod.getName();
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return;
        }
        String returnClassName = returnType.getPresentableText();
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[0];
        //带package的class名称
        String parameterClassWithPackage = psiParameter.getType().getInternalCanonicalText();
        //为了解析字段，这里需要加载参数的class
        JavaPsiFacade facade = JavaPsiFacade.getInstance(psiMethod.getProject());
        PsiClass paramentClass = facade.findClass(parameterClassWithPackage, GlobalSearchScope.allScope(psiMethod.getProject()));
        if (paramentClass == null) {
            if (parameterClassWithPackage.contains("java.util.List")) {
                parameterClassWithPackage = parameterClassWithPackage.split("<")[1].replace(">", "");
                paramentClass = facade.findClass(parameterClassWithPackage, GlobalSearchScope.allScope(psiMethod.getProject()));
            }
            if (paramentClass == null) {
                return;
            }
        }
        List<PsiField> paramentFields = new CollectionListModel<PsiField>(paramentClass.getFields()).getItems();
        String methodText = getMethodText(methodName, returnClassName, psiParameter, paramentFields);
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiMethod.getProject());
        PsiMethod toMethod = elementFactory.createMethodFromText(methodText, psiMethod);
        psiMethod.replace(toMethod);
    }

    /**
     * @param methodName      方法名称
     * @param returnClassName 返回的值的class名称
     * @param psiParameter    方法参数第一个值
     * @param paramentFields  方法参数的class里field 列表
     * @return 方法体的字符串
     */
    private String getMethodText(String methodName, String returnClassName, PsiParameter psiParameter, List<PsiField> paramentFields) {

        String returnObjName = returnClassName.substring(0, 1).toLowerCase() + returnClassName.substring(1);
        String parameterClass = psiParameter.getText();
        String parameterName = psiParameter.getName();
        //如果是集合
        if (returnClassName.contains("List")) {
            return getListConvertMethod(methodName, returnClassName, paramentFields, returnObjName, parameterClass, parameterName);
        }
        return getSingleConvertMethod(methodName, returnClassName, paramentFields, returnObjName, parameterClass, parameterName);
    }

    private String getListConvertMethod(String methodName, String returnClassName, List<PsiField> paramentFields, String returnObjName, String parameterClass, String parameterName) {
        String singleReturnObjName = returnObjName.replace("list<", "").replace(">", "");
        String singleParameterName = parameterName.replace("List", "");
        StringBuilder builder = new StringBuilder("public static " + returnClassName + " " + methodName + " (");
        builder.append(parameterClass + " ) {\n");
        builder.append("if ( " + parameterName + "== null ){\n").append("return Collections.emptyList();\n}")
                .append(returnClassName + " " + getFirstLowerCase(singleReturnObjName) + "List = new ArrayList<>();\n");
        builder.append(parameterName).append(".forEach(").append(parameterName.replace("List", "")).append("->{\n");
        builder.append(singleReturnObjName + " " + getFirstLowerCase(singleReturnObjName) + " = new " + singleReturnObjName + "();\n");
        for (PsiField field : paramentFields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.STATIC) || modifierList.hasModifierProperty(PsiModifier.FINAL)
                    || modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                continue;
            }
            builder.append(getFirstLowerCase(singleReturnObjName) + ".set" + getFirstUpperCase(field.getName()) + "(" + singleParameterName + ".get"
                    + getFirstUpperCase(field.getName()) + "());\n");

        }
        builder.append(getFirstLowerCase(singleReturnObjName) + "List.add(" + getFirstLowerCase(singleReturnObjName) + ");\n");
        builder.append("});\n");
        builder.append("return " + getFirstLowerCase(singleReturnObjName) + "List;\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String getSingleConvertMethod(String methodName, String returnClassName, List<PsiField> paramentFields, String returnObjName, String parameterClass, String parameterName) {
        StringBuilder builder = new StringBuilder("public static " + returnClassName + " " + methodName + " (");
        builder.append(parameterClass + " ) {\n");
        builder.append("if ( " + parameterName + "== null ){\n").append("return null;\n}")
                .append(returnClassName + " " + returnObjName + "= new " + returnClassName + "();\n");
        for (PsiField field : paramentFields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.STATIC) || modifierList.hasModifierProperty(PsiModifier.FINAL)
                    || modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                continue;
            }
            builder.append(
                    returnObjName + ".set" + getFirstUpperCase(field.getName()) + "(" + parameterName + ".get" + getFirstUpperCase(field.getName())
                            + "());\n");
        }
        builder.append("return " + returnObjName + ";\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String getFirstUpperCase(String oldStr) {
        return oldStr.substring(0, 1).toUpperCase() + oldStr.substring(1);
    }

    private String getFirstLowerCase(String oldStr) {
        return oldStr.substring(0, 1).toLowerCase() + oldStr.substring(1);
    }

    private PsiMethod getPsiMethodFromContext(AnActionEvent e) {
        PsiElement elementAt = getPsiElement(e);
        if (elementAt == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    }

    private PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }
        //用来获取当前光标处的PsiElement
        int offset = editor.getCaretModel().getOffset();
        return psiFile.findElementAt(offset);
    }
}
