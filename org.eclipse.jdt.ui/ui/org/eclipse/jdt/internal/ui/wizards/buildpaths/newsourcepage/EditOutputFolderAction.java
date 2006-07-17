/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ISetSelectionTarget;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier.IClasspathModifierListener;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElementAttribute;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.OutputLocationDialog;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.ClasspathModifierQueries.OutputFolderQuery;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.ClasspathModifierQueries.OutputFolderValidator;

public class EditOutputFolderAction extends Action implements ISelectionChangedListener {

	private final IWorkbenchSite fSite;
	private IJavaProject fJavaProject;
	private CPListElement fSelectedElement;
	private final IRunnableContext fContext;
	private final IClasspathModifierListener fListener;

	public EditOutputFolderAction(final IWorkbenchSite site) {
		this(site, PlatformUI.getWorkbench().getProgressService(), null);
	}

	public EditOutputFolderAction(IWorkbenchSite site, IRunnableContext context, IClasspathModifierListener listener) {
		super(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_EditOutput_label, JavaPluginImages.DESC_ELCL_CONFIGURE_OUTPUT_FOLDER);
		
		fSite= site;
		fContext= context;
		fListener= listener;
		
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_EditOutput_tooltip);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_CONFIGURE_OUTPUT_FOLDER);
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		try {

			final Shell shell= getShell();

			final List classpathEntries= ClasspathModifier.getExistingEntries(fJavaProject);

			final CPListElement element= ClasspathModifier.getClasspathEntry(classpathEntries, fSelectedElement);
			final int index= classpathEntries.indexOf(element);

			final OutputLocationDialog dialog= new OutputLocationDialog(shell, element, classpathEntries);
			if (dialog.open() != Window.OK)
				return;

			classpathEntries.add(index, element);

			final boolean removeProjectFromClasspath;
			final IPath defaultOutputLocation= fJavaProject.getOutputLocation().makeRelative();
			final IPath newDefaultOutputLocation;
			if (defaultOutputLocation.segmentCount() == 1) {
				//Project folder is output location
				final OutputFolderValidator outputFolderValidator= new OutputFolderValidator(null, fJavaProject) {
					public boolean validate(IPath outputLocation) {
						return true;
					}
				};
				final OutputFolderQuery outputFolderQuery= ClasspathModifierQueries.getDefaultFolderQuery(shell, defaultOutputLocation);
				if (outputFolderQuery.doQuery(true, outputFolderValidator, fJavaProject)) {
					newDefaultOutputLocation= outputFolderQuery.getOutputLocation();
					removeProjectFromClasspath= outputFolderQuery.removeProjectFromClasspath();
				} else {
					return;
				}
			} else {
				removeProjectFromClasspath= false;
				newDefaultOutputLocation= defaultOutputLocation;
			}
			
			try {
				final IRunnableWithProgress runnable= new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						try {
							setOutputLocation(element, dialog.getOutputLocation(), classpathEntries, newDefaultOutputLocation, removeProjectFromClasspath, monitor);
						} catch (CoreException e) {
							throw new InvocationTargetException(e);
						}
					}
				};
				fContext.run(false, false, runnable);
			} catch (final InvocationTargetException e) {
				if (e.getCause() instanceof CoreException) {
					showExceptionDialog((CoreException)e.getCause());
				} else {
					JavaPlugin.log(e);
				}
			} catch (final InterruptedException e) {
			}
			
		} catch (final CoreException e) {
			showExceptionDialog(e);
		}
	}

	private void setOutputLocation(final CPListElement entry, final IPath outputLocation, final List existingEntries, final IPath defaultOutputLocation, final boolean removeProjectFromClasspath, final IProgressMonitor monitor) throws CoreException, InterruptedException {
		try {
			monitor.beginTask(NewWizardMessages.EditOutputFolderAction_ProgressMonitorDescription, 4);
			if (!defaultOutputLocation.equals(fJavaProject.getOutputLocation().makeRelative())) {
				fJavaProject.setOutputLocation(defaultOutputLocation, new SubProgressMonitor(monitor, 1));
			} else {
				monitor.worked(1);
			}
			
			if (removeProjectFromClasspath) {
				ClasspathModifier.removeFromClasspath(fJavaProject, existingEntries, new SubProgressMonitor(monitor, 1));
			} else {
				monitor.worked(1);
			}
	
			if (outputLocation != null) {
				ClasspathModifier.exclude(outputLocation, existingEntries, new ArrayList(), fJavaProject, new SubProgressMonitor(monitor, 1));
				entry.setAttribute(CPListElement.OUTPUT, outputLocation);
			} else {
				monitor.worked(1);
			}
			
			ClasspathModifier.commitClassPath(existingEntries, fJavaProject, fListener, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
	}

	public void selectionChanged(final SelectionChangedEvent event) {
		final ISelection selection = event.getSelection();
		if (selection instanceof IStructuredSelection) {
			setEnabled(canHandle((IStructuredSelection) selection));
		} else {
			setEnabled(canHandle(StructuredSelection.EMPTY));
		}
	}

	public boolean canHandle(final IStructuredSelection elements) {
		if (elements.size() != 1)
			return false;

		final Object element= elements.getFirstElement();
		try {
			if (element instanceof IPackageFragmentRoot) {
				final IPackageFragmentRoot root= (IPackageFragmentRoot)element;
				fJavaProject= root.getJavaProject();
				if (fJavaProject == null)
					return false;
				
				final IClasspathEntry entry= ClasspathModifier.getClasspathEntryFor(root.getPath(), fJavaProject, IClasspathEntry.CPE_SOURCE);
				if (entry == null)
					return false;
				
				fSelectedElement= CPListElement.createFromExisting(entry, fJavaProject);
				
				return root.getKind() == IPackageFragmentRoot.K_SOURCE;
			} else if (element instanceof IJavaProject) {
				IJavaProject project= (IJavaProject)element;	
				if (ClasspathModifier.isSourceFolder(project)) {
					fJavaProject= project;
					
					final IClasspathEntry entry= ClasspathModifier.getClasspathEntryFor(project.getPath(), fJavaProject, IClasspathEntry.CPE_SOURCE);
					fSelectedElement= CPListElement.createFromExisting(entry, fJavaProject);
					
					return true;
				}
			} else if (element instanceof CPListElementAttribute) {
				CPListElementAttribute attribute= (CPListElementAttribute)element;
				
				fJavaProject= attribute.getParent().getJavaProject();
				fSelectedElement= attribute.getParent();
				
				if (attribute.getKey() == CPListElement.OUTPUT)
					return true;
			}

		} catch (final JavaModelException e) {
			return false;
		}
		return false;
	}

	private void showExceptionDialog(final CoreException exception) {
		showError(exception, getShell(), NewWizardMessages.EditOutputFolderAction_ErrorDescription, exception.getMessage());
	}

	private void showError(CoreException e, Shell shell, String title, String message) {
		IStatus status= e.getStatus();
		if (status != null) {
			ErrorDialog.openError(shell, message, title, status);
		} else {
			MessageDialog.openError(shell, title, message);
		}
	}
	
	private Shell getShell() {
		if (fSite == null)
			return JavaPlugin.getActiveWorkbenchShell();
		
	    return fSite.getShell() != null ? fSite.getShell() : JavaPlugin.getActiveWorkbenchShell();
    }
	
	protected void selectAndReveal(final ISelection selection) {
		// validate the input
		IWorkbenchPage page= fSite.getPage();
		if (page == null)
			return;

		// get all the view and editor parts
		List parts= new ArrayList();
		IWorkbenchPartReference refs[]= page.getViewReferences();
		for (int i= 0; i < refs.length; i++) {
			IWorkbenchPart part= refs[i].getPart(false);
			if (part != null)
				parts.add(part);
		}
		refs= page.getEditorReferences();
		for (int i= 0; i < refs.length; i++) {
			if (refs[i].getPart(false) != null)
				parts.add(refs[i].getPart(false));
		}

		Iterator itr= parts.iterator();
		while (itr.hasNext()) {
			IWorkbenchPart part= (IWorkbenchPart) itr.next();

			// get the part's ISetSelectionTarget implementation
			ISetSelectionTarget target= null;
			if (part instanceof ISetSelectionTarget)
				target= (ISetSelectionTarget) part;
			else
				target= (ISetSelectionTarget) part.getAdapter(ISetSelectionTarget.class);

			if (target != null) {
				// select and reveal resource
				final ISetSelectionTarget finalTarget= target;
				page.getWorkbenchWindow().getShell().getDisplay().asyncExec(new Runnable() {
					public void run() {
						finalTarget.selectReveal(selection);
					}
				});
			}
		}
	}
	
	protected List getSelectedElements() {
		ArrayList result= new ArrayList();
		result.add(fSelectedElement);
		return result;
	}

}
