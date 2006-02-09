/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.cheatsheets.composite.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.cheatsheets.ICheatSheetManager;
import org.eclipse.ui.cheatsheets.ICompositeCheatSheetTask;
import org.eclipse.ui.cheatsheets.ITaskEditor;
import org.eclipse.ui.internal.cheatsheets.CheatSheetPlugin;
import org.eclipse.ui.internal.cheatsheets.composite.parser.ICompositeCheatsheetTags;
import org.eclipse.ui.internal.cheatsheets.data.CheatSheetSaveHelper;
import org.eclipse.ui.internal.cheatsheets.data.IParserTags;
import org.eclipse.ui.internal.cheatsheets.views.CheatSheetManager;

/**
 * Class to save and restore composite cheatsheet state using a memento
 * There is a child memento for each task which contains keys for the
 * state and percentage complete. There is also a grandchild memento for 
 * each task that has been started.
 */

public class CompositeCheatSheetSaveHelper extends CheatSheetSaveHelper {
	private static final String DOT_XML = ".xml"; //$NON-NLS-1$
	private Map taskMementoMap;

	/**
	 * Constructor 
	 */
	public CompositeCheatSheetSaveHelper() {
		super();
	}

	public IStatus loadCompositeState(CompositeCheatSheetModel model) {
		XMLMemento readMemento = CheatSheetPlugin.getPlugin().readMemento(model.getId() + DOT_XML);
		if (readMemento == null) {
			return Status.OK_STATUS;
		}	
        taskMementoMap = createTaskMap(readMemento);
        loadTaskState(taskMementoMap, (CheatSheetTask)model.getRootTask());
        loadCheatsheetManagerData(readMemento, model.getCheatSheetManager());
        return Status.OK_STATUS;
	}

	private Map createTaskMap(XMLMemento readMemento) {
		Map map = new HashMap();
		IMemento[] tasks = readMemento.getChildren(ICompositeCheatsheetTags.TASK);
		for (int i = 0; i < tasks.length; i++) {
			String taskId = tasks[i].getString(ICompositeCheatsheetTags.TASK_ID);
			if (taskId != null) {
			    map.put(taskId, tasks[i]);
			}
		}
		return map;
	}

	private void loadTaskState(Map taskMap, CheatSheetTask task) {
		ICompositeCheatSheetTask[] children = task.getSubtasks();
		IMemento memento = (IMemento)taskMap.get(task.getId());
		if (memento != null) {
			String state = memento.getString(ICompositeCheatsheetTags.STATE);
			if (state != null) {
				task.setState(Integer.parseInt(state));
			}
			String percentage = memento.getString(ICompositeCheatsheetTags.PERCENTAGE_COMPLETE);
			if (percentage != null) {
				task.setPercentageComplete(Integer.parseInt(percentage));
			}
		}
		for (int i = 0; i < children.length; i++) {		
			loadTaskState(taskMap, (CheatSheetTask) children[i]);
		}
	}
	
	private void loadCheatsheetManagerData(XMLMemento readMemento, ICheatSheetManager manager) {
		if (manager == null) {
			return;
		}
		IMemento[] children = readMemento.getChildren(ICompositeCheatsheetTags.CHEAT_SHEET_MANAGER);
		for (int i = 0; i < children.length; i++) {
			IMemento childMemento = children[i];
			String key = childMemento.getString(ICompositeCheatsheetTags.KEY);
			String value = childMemento.getString(ICompositeCheatsheetTags.VALUE);
			manager.setData(key, value);
		}
	}

	/**
	 * Save the state of a composite cheat sheet model
	 * @param model
	 * @return
	 */
	public IStatus saveCompositeState(CompositeCheatSheetModel model) {
		XMLMemento writeMemento = XMLMemento.createWriteRoot(ICompositeCheatsheetTags.COMPOSITE_CHEATSHEET_STATE);
		writeMemento.putString(IParserTags.ID, model.getId());		
        saveTaskState(writeMemento, (CheatSheetTask)model.getRootTask());
        saveCheatSheetManagerData(writeMemento, model.getCheatSheetManager());
		taskMementoMap = createTaskMap(writeMemento);
		return CheatSheetPlugin.getPlugin().saveMemento(writeMemento, model.getId() + DOT_XML);
	}

	private void saveCheatSheetManagerData(XMLMemento writeMemento, ICheatSheetManager manager) {
		if (!(manager instanceof CheatSheetManager)) {
			return;
		}		
		Map data = ((CheatSheetManager)manager).getData();
		for (Iterator iter = data.keySet().iterator(); iter.hasNext();) {
			String key = (String)iter.next();
			String value = manager.getData(key);
			IMemento childMemento = writeMemento.createChild(ICompositeCheatsheetTags.CHEAT_SHEET_MANAGER);
			childMemento.putString(ICompositeCheatsheetTags.KEY, key);
			childMemento.putString(ICompositeCheatsheetTags.VALUE, value);		
		}
	}

	private void saveTaskState(IMemento writeMemento, CheatSheetTask task) {
		IMemento childMemento = writeMemento.createChild(ICompositeCheatsheetTags.TASK);
		childMemento.putString(ICompositeCheatsheetTags.TASK_ID, task.getId());
		childMemento.putString(ICompositeCheatsheetTags.STATE, Integer.toString(task.getState())); 
		childMemento.putString(ICompositeCheatsheetTags.PERCENTAGE_COMPLETE, Integer.toString(task.getPercentageComplete())); 

		ICompositeCheatSheetTask[] subtasks = task.getSubtasks();
		ITaskEditor editor = task.getEditor();
		if (editor != null) {
			IMemento taskDataMemento = childMemento.createChild(ICompositeCheatsheetTags.TASK_DATA);
			editor.saveState(taskDataMemento);
		} else {
			// The task has not been started so save its previous state
			IMemento taskData = getTaskMemento(task.getId());
			if (taskData != null) {
				IMemento previousDataMemento = childMemento.createChild(ICompositeCheatsheetTags.TASK_DATA);
				previousDataMemento.putMemento(taskData);
			}
		}
		for (int i = 0; i < subtasks.length; i++) {
			saveTaskState(writeMemento, (CheatSheetTask)subtasks[i]);
		}
	}

	public IMemento getTaskMemento(String id) {
		if (taskMementoMap == null) {
			return null;
		}
	    IMemento childMemento = (IMemento)taskMementoMap.get(id);
	    if (childMemento == null) {
	    	return  null;
	    }
	    return childMemento.getChild(ICompositeCheatsheetTags.TASK_DATA);
	}

	public void clearTaskMementos() {
		taskMementoMap = null;
	}

}