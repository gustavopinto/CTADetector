package br.ufpe.cin.concurrency.fjdetector.view;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import br.ufpe.cin.concurrency.fjdetector.util.Result;

public class ResultViewer extends ViewPart {
	public static final String ID = "br.ufpe.cin.concurrency.fjdetector.view.ResultViewer";
	 
    private TableViewer viewer;
    
    @Override
    public void createPartControl(Composite parent) {
        viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL
                | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
        createColumns(parent, viewer);
        final Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        viewer.setContentProvider(new ArrayContentProvider());
        getSite().setSelectionProvider(viewer);

        GridData gridData = new GridData();
        gridData.verticalAlignment = GridData.FILL;
        gridData.horizontalSpan = 2;
        gridData.grabExcessHorizontalSpace = true;
        gridData.grabExcessVerticalSpace = true;
        gridData.horizontalAlignment = GridData.FILL;
        viewer.getControl().setLayoutData(gridData);
    }
    
    public void clearData() {
	    viewer.getTable().clearAll();
	    viewer.refresh();
    }
    
    public void setInput(List<Result> detections) {
		viewer.getTable().clearAll();
		viewer.refresh();
		viewer.setInput(detections);
    }
    
    private void createColumns(final Composite parent, final TableViewer viewer) {
        String[] titles = {"Project name",  "Black List", "Path",  "Class", "Line" };
        int[] bounds = { 150, 200, 250, 250, 40 };

        TableViewerColumn col = createTableViewerColumn(titles[0], bounds[0]);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Result) element).getProjectName();
            }
        });

        col = createTableViewerColumn(titles[1], bounds[1]);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Result) element).getBlackList();
            }
        });

        col = createTableViewerColumn(titles[2], bounds[2]);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Result) element).getFile().toString();
            }
        });

        col = createTableViewerColumn(titles[3], bounds[3]);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Result) element).getClassName();
            }
        });

        col = createTableViewerColumn(titles[4], bounds[4]);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Result) element).getLine();
            }
        });

        viewer.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                StructuredSelection selection = (StructuredSelection) viewer.getSelection();
                Result elem = (Result) selection.getFirstElement();
                IEditorRegistry editorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
                
                IFile file = elem.getFile();
                
                String editorId = editorRegistry.getDefaultEditor(file.getFullPath().toString()).getId();
                
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                
                try {
                    AbstractTextEditor ePart = (AbstractTextEditor) page.openEditor(new FileEditorInput(file), editorId);
                    IDocument document = ePart.getDocumentProvider().getDocument(ePart.getEditorInput());
                    if (document != null) {
                        IRegion lineInfo = null;
                        try {
                            lineInfo = document.getLineInformation(Integer.valueOf(elem.getLine()) - 1);
                        } catch (BadLocationException e) {
                            // ignored
                        }
                        if (lineInfo != null) {
                            ePart.selectAndReveal(lineInfo.getOffset(), lineInfo.getLength());
                        }
                    }
                } catch (PartInitException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private TableViewerColumn createTableViewerColumn(String title, int bound) {
        final TableViewerColumn viewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        final TableColumn column = viewerColumn.getColumn();
        column.setText(title);
        column.setWidth(bound);
        column.setResizable(true);
        column.setMoveable(true);
        return viewerColumn;
    }

    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }
}
