package org.eclipse.jdt.ui.tests.refactoring;

import java.util.Arrays;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.tests.refactoring.infra.SourceCompareUtil;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.structure.PushDownRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.PushDownRefactoring.MemberActionInfo;

public class PushDownTests extends RefactoringTest {

	private static final Class clazz= PushDownTests.class;
	
	private static final String REFACTORING_PATH= "PushDown/";

	public PushDownTests(String name) {
		super(name);
	}

	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}

	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	private static PushDownRefactoring createRefactoring(IMember[] members){
		return new PushDownRefactoring(members, JavaPreferencesSettings.getCodeGenerationSettings());
	}

	private PushDownRefactoring createRefactoringPrepareForInputCheck(String[] selectedMethodNames, String[][] selectedMethodSignatures, 
						String[] selectedFieldNames, 
						String[] namesOfMethodsToPullUp, String[][] signaturesOfMethodsToPullUp, 
						String[] namesOfFieldsToPullUp, 
						String[] namesOfMethodsToDeclareAbstract, String[][] signaturesOfMethodsToDeclareAbstract,
						ICompilationUnit cu) throws JavaModelException {
							
		IType type= getType(cu, "A");
		IMethod[] selectedMethods= TestUtil.getMethods(type, selectedMethodNames, selectedMethodSignatures);
		IField[] selectedFields= TestUtil.getFields(type, selectedFieldNames);
		IMember[] selectedMembers= TestUtil.merge(selectedFields, selectedMethods);
		
		PushDownRefactoring ref= createRefactoring(selectedMembers);
		assertTrue("preactivation", ref.checkPreactivation().isOK());
		assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
		
		prepareForInputCheck(ref, selectedMethods, selectedFields, namesOfMethodsToPullUp, signaturesOfMethodsToPullUp, namesOfFieldsToPullUp, namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract);
		return ref;
	}

	private void prepareForInputCheck(PushDownRefactoring ref, IMethod[] selectedMethods, IField[] selectedFields, String[] namesOfMethodsToPullUp, String[][] signaturesOfMethodsToPullUp, String[] namesOfFieldsToPullUp, String[] namesOfMethodsToDeclareAbstract, String[][] signaturesOfMethodsToDeclareAbstract) {
		IMethod[] methodsToPushDown= TestUtil.findMethods(selectedMethods, namesOfMethodsToPullUp, signaturesOfMethodsToPullUp);
		IField[] fieldsToPushDown= TestUtil.findFields(selectedFields, namesOfFieldsToPullUp);
		List membersToPushDown= Arrays.asList(TestUtil.merge(methodsToPushDown, fieldsToPushDown));
		List methodsToDeclareAbstract= Arrays.asList(TestUtil.findMethods(selectedMethods, namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract));
		
		PushDownRefactoring.MemberActionInfo[] infos= ref.getMemberActionInfos();
		for (int i= 0; i < infos.length; i++) {
			if (membersToPushDown.contains(infos[i].getMember())){
				infos[i].setAction(MemberActionInfo.PUSH_DOWN_ACTION);
				assertTrue(! methodsToDeclareAbstract.contains(infos[i].getMember()));
			}
			if (methodsToDeclareAbstract.contains(infos[i].getMember())){
				infos[i].setAction(MemberActionInfo.PUSH_ABSTRACT_ACTION);
				assertTrue(! membersToPushDown.contains(infos[i].getMember()));
			}
		}
	}

	private void helper(String[] selectedMethodNames, String[][] selectedMethodSignatures,
						String[] selectedFieldNames,
						String[] namesOfMethodsToPullUp, String[][] signaturesOfMethodsToPullUp, 
						String[] namesOfFieldsToPullUp, String[] namesOfMethodsToDeclareAbstract, 
						String[][] signaturesOfMethodsToDeclareAbstract, String[] additionalCuNames, String[] additionalPackNames) throws Exception{
		ICompilationUnit cuA= createCUfromTestFile(getPackageP(), "A");
		
		IPackageFragment[] addtionalPacks= createAdditionalPackages(additionalCuNames, additionalPackNames);
		ICompilationUnit[] additonalCus= createAdditionalCus(additionalCuNames, addtionalPacks);
		
		try{
			PushDownRefactoring ref= createRefactoringPrepareForInputCheck(selectedMethodNames, selectedMethodSignatures, selectedFieldNames, namesOfMethodsToPullUp, signaturesOfMethodsToPullUp, namesOfFieldsToPullUp, namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, cuA);

			RefactoringStatus checkInputResult= ref.checkInput(new NullProgressMonitor());
			assertTrue("precondition was supposed to pass but got " + checkInputResult.toString(), checkInputResult.isOK());	
			performChange(ref.createChange(new NullProgressMonitor()));

			String expected= getFileContents(getOutputTestFileName("A"));
			String actual= cuA.getSource();
			SourceCompareUtil.compare(actual, expected);
			
			for (int i= 0; i < additonalCus.length; i++) {
				ICompilationUnit unit= additonalCus[i];
				String expectedS= getFileContents(getOutputTestFileName(additionalCuNames[i]));
				String actualS= unit.getSource();
				SourceCompareUtil.compare(actualS, expectedS);
			}
			
		} finally{
			performDummySearch();
			cuA.delete(false, null);
			for (int i= 0; i < additonalCus.length; i++) {
				additonalCus[i].delete(false, null);
			}
			for (int i= 0; i < addtionalPacks.length; i++) {
				if (! addtionalPacks[i].equals(getPackageP()))
					addtionalPacks[i].delete(false, null);
			}
		}	
	}

	private ICompilationUnit[] createAdditionalCus(String[] additionalCuNames, IPackageFragment[] addtionalPacks) throws Exception {
		ICompilationUnit[] additonalCus= new ICompilationUnit[0];
		if (additionalCuNames != null){
			additonalCus= new ICompilationUnit[additionalCuNames.length];
			for (int i= 0; i < additonalCus.length; i++) {
				additonalCus[i]= createCUfromTestFile(addtionalPacks[i], additionalCuNames[i]);
			}
		}
		return additonalCus;
	}

	private IPackageFragment[] createAdditionalPackages(String[] additionalCuNames, String[] additionalPackNames) {
		IPackageFragment[] additionalPacks= new IPackageFragment[0];
		if (additionalPackNames != null){
			additionalPacks= new IPackageFragment[additionalPackNames.length];
			assertTrue(additionalPackNames.length == additionalCuNames.length);
			for (int i= 0; i < additionalPackNames.length; i++) {
				additionalPacks[i]= getRoot().getPackageFragment(additionalPackNames[i]);
			}
		}
		return additionalPacks;
	}

	private void failActivationHelper(String[] selectedMethodNames, String[][] selectedMethodSignatures,
										String[] selectedFieldNames,
										String[] namesOfMethodsToPullUp, String[][] signaturesOfMethodsToPullUp, 
										String[] namesOfFieldsToPullUp, String[] namesOfMethodsToDeclareAbstract, 
										String[][] signaturesOfMethodsToDeclareAbstract,
										int expectedSeverity) throws Exception{
												
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "A");
			IMethod[] selectedMethods= TestUtil.getMethods(type, selectedMethodNames, selectedMethodSignatures);
			IField[] selectedFields= TestUtil.getFields(type, selectedFieldNames);
			IMember[] selectedMembers= TestUtil.merge(selectedFields, selectedMethods);
		
			PushDownRefactoring ref= createRefactoring(selectedMembers);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertEquals("activation was expected to fail", expectedSeverity, ref.checkActivation(new NullProgressMonitor()).getSeverity());
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}	
	}

	private void failInputHelper(String[] selectedMethodNames, String[][] selectedMethodSignatures,
											String[] selectedFieldNames,
											String[] namesOfMethodsToPullUp, String[][] signaturesOfMethodsToPullUp, 
											String[] namesOfFieldsToPullUp, String[] namesOfMethodsToDeclareAbstract, 
											String[][] signaturesOfMethodsToDeclareAbstract,
											int expectedSeverity) throws Exception{
												
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			PushDownRefactoring ref= createRefactoringPrepareForInputCheck(selectedMethodNames, selectedMethodSignatures, selectedFieldNames, namesOfMethodsToPullUp, signaturesOfMethodsToPullUp, namesOfFieldsToPullUp, namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, cu);
			RefactoringStatus checkInputResult= ref.checkInput(new NullProgressMonitor());
			assertEquals("precondition was expected to fail", expectedSeverity, checkInputResult.getSeverity());	
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}	
	}
	
	//--------------------------------------------------------
	
	public void test0() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test1() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test2() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test3() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test4() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test5() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test6() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test7() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test8() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test9() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test10() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test11() throws Exception{
		String[] selectedMethodNames= {"m"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test12() throws Exception{
		String[] selectedMethodNames= {};
		String[][] selectedMethodSignatures= {};
		String[] selectedFieldNames= {"f"};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {"f"};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test13() throws Exception{
		String[] selectedMethodNames= {};
		String[][] selectedMethodSignatures= {};
		String[] selectedFieldNames= {"f"};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {"f"};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test14() throws Exception{
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test15() throws Exception{
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test16() throws Exception{
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test17() throws Exception{
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test18() throws Exception{
		String[] selectedMethodNames= {"f", "m"};
		String[][] selectedMethodSignatures= {new String[0], new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test19() throws Exception{
		String[] selectedMethodNames= {"f", "m"};
		String[][] selectedMethodSignatures= {new String[0], new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, null, null);
	}

	public void test20() throws Exception{
		String[] selectedMethodNames= {"f", "m"};
		String[][] selectedMethodSignatures= {new String[0], new String[0]};
		String[] selectedFieldNames= {"i"};
		String[] namesOfMethodsToPushDown= {"f"};
		String[][] signaturesOfMethodsToPushDown= {new String[0]};
		String[] namesOfFieldsToPushDown= {"i"};
		String[] namesOfMethodsToDeclareAbstract= {"m"};
		String[][] signaturesOfMethodsToDeclareAbstract= {new String[0]};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, 
			   new String[]{"B"}, new String[]{"p"});
	}

	public void test21() throws Exception{
		String[] selectedMethodNames= {"f", "m"};
		String[][] selectedMethodSignatures= {new String[0], new String[0]};
		String[] selectedFieldNames= {"i"};
		String[] namesOfMethodsToPushDown= {"f", "m"};
		String[][] signaturesOfMethodsToPushDown= {new String[0], new String[0]};
		String[] namesOfFieldsToPushDown= {"i"};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		helper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract, 
			   new String[]{"B", "C"}, new String[]{"p", "p"});
	}
	
	public void testFail0() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failActivationHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.FATAL);
	}

	public void testFail1() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failActivationHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.FATAL);
	}

	public void testFail2() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}

	public void testFail3() throws Exception {
		String[] selectedMethodNames= {};
		String[][] selectedMethodSignatures= {};
		String[] selectedFieldNames= {"i"};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= selectedFieldNames;
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}

	public void testFail4() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}

	public void testFail5() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}

	public void testFail6() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= selectedMethodNames;
		String[][] signaturesOfMethodsToPushDown= selectedMethodSignatures;
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= {};
		String[][] signaturesOfMethodsToDeclareAbstract= {};
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}

	public void testFail7() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}

	public void testFail8() throws Exception {
		String[] selectedMethodNames= {"f"};
		String[][] selectedMethodSignatures= {new String[0]};
		String[] selectedFieldNames= {};
		String[] namesOfMethodsToPushDown= {};
		String[][] signaturesOfMethodsToPushDown= {};
		String[] namesOfFieldsToPushDown= {};
		String[] namesOfMethodsToDeclareAbstract= selectedMethodNames;
		String[][] signaturesOfMethodsToDeclareAbstract= selectedMethodSignatures;
		
		failInputHelper(selectedMethodNames, selectedMethodSignatures, 
			   selectedFieldNames,	
			   namesOfMethodsToPushDown, signaturesOfMethodsToPushDown, 
			   namesOfFieldsToPushDown, 
			   namesOfMethodsToDeclareAbstract, signaturesOfMethodsToDeclareAbstract,
			   RefactoringStatus.ERROR);
	}
}
