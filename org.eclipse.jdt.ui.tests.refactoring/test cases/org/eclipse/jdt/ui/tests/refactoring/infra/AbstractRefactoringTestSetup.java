/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.refactoring.infra;

import java.util.Hashtable;

import junit.extensions.TestSetup;
import junit.framework.Test;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

public class AbstractRefactoringTestSetup extends TestSetup {

	private boolean fWasAutobuild;
	
	public AbstractRefactoringTestSetup(Test test) {
		super(test);
	}

	protected void setUp() throws Exception {
		super.setUp();
		fWasAutobuild= JavaProjectHelper.setAutoBuilding(false);
		if (JavaPlugin.getActivePage() != null)
			JavaPlugin.getActivePage().close();

		Hashtable options= TestOptions.getFormatterOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, "0");
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		JavaCore.setOptions(options);
		TestOptions.initializeCodeGenerationOptions();
		JavaPlugin.getDefault().getCodeTemplateStore().load();		
		
		StringBuffer comment= new StringBuffer();
		comment.append("/**\n");
		comment.append(" * ${tags}\n");
		comment.append(" */");
		JavaPlugin.getDefault().getCodeTemplateStore().findTemplate(CodeTemplateContextType.CONSTRUCTORCOMMENT).setPattern(comment.toString());
	}
	
	protected void tearDown() throws Exception {
		JavaProjectHelper.setAutoBuilding(fWasAutobuild);
		/* 
		 * ensure the workbench state gets saved when running with the Automated Testing Framework
         * TODO: remove when https://bugs.eclipse.org/bugs/show_bug.cgi?id=71362 is fixed
         */
		/* Not needed for JDT/UI tests right now.
		StackTraceElement[] elements=  new Throwable().getStackTrace();
		for (int i= 0; i < elements.length; i++) {
			StackTraceElement element= elements[i];
			if (element.getClassName().equals("org.eclipse.test.EclipseTestRunner")) {
				PlatformUI.getWorkbench().close();
				break;
			}
		}
		*/
		super.tearDown();
	}
}
