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
package org.eclipse.jdt.ui.tests.refactoring.reorg;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllReorgPerformanceTests {
	
	public static Test suite() {
		TestSuite suite= new TestSuite("All Refactoring Performance Tests"); //$NON-NLS-1$
		suite.addTest(RenamePackagePerfTests1.suite());
		suite.addTest(RenamePackagePerfTests2.suite());
		
		suite.addTest(RenameTypePerfAcceptanceTests.suite());
		suite.addTest(RenameTypePerfTests1.suite());
		suite.addTest(RenameTypePerfTests2.suite());
		
		suite.addTest(RenameMethodPerfTests1.suite());
		suite.addTest(RenameMethodPerfTests2.suite());
		suite.addTest(RenameMethodWithHidingPerfTests.suite());
		suite.addTest(RenameMethodWithOverloadPerfTests.suite());
		
		suite.addTest(MoveCompilationUnitPerfTests1.suite());
		suite.addTest(MoveCompilationUnitPerfTests2.suite());
		
		suite.addTest(MoveStaticMembersPerfTests1.suite());
		suite.addTest(MoveStaticMembersPerfTests2.suite());
		
		return suite;
	}
}
