/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static com.forgerock.opendj.util.OperatingSystem.isMacOS;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;

/** The menu bar that appears on the main panel. */
public class MainMenuBar extends GenericMenuBar
{
  private static final long serialVersionUID = 6441273044772077947L;

  private GenericDialog dlg;
  private RefreshOptionsPanel panel;

  /**
   * Constructor.
   * @param info the control panel information.
   */
  public MainMenuBar(ControlPanelInfo info)
  {
    super(info);

    addMenus();

    if (isMacOS())
    {
      setMacOSQuitHandler();
    }
  }

  /** Method that can be overwritten to set specific menus. */
  protected void addMenus()
  {
    add(createFileMenuBar());
    add(createViewMenuBar());
    add(createHelpMenuBar());
  }

  /**
   * The method called when the user clicks on quick.  It will check that there
   * are not ongoing tasks.  If there are tasks, it will ask the user for
   * confirmation to quit.
   */
  public void quitClicked()
  {
    Set<String> runningTasks = new HashSet<>();
    for (Task task : getInfo().getTasks())
    {
      if (task.getState() == Task.State.RUNNING)
      {
        runningTasks.add(task.getTaskDescription().toString());
      }
    }
    boolean confirmed = true;
    if (!runningTasks.isEmpty())
    {
      String allTasks = Utilities.getStringFromCollection(runningTasks, "<br>");
      LocalizableMessage title = INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get();
      LocalizableMessage msg =
        INFO_CTRL_PANEL_RUNNING_TASKS_CONFIRMATION_DETAILS.get(allTasks);
      confirmed = Utilities.displayConfirmationDialog(
          Utilities.getParentDialog(this), title, msg);
    }
    if (confirmed)
    {
      System.exit(0);
    }
  }

  /**
   * Creates the File menu bar.
   * @return the File menu bar.
   */
  private JMenu createFileMenuBar()
  {
    JMenu menu = Utilities.createMenu(INFO_CTRL_PANEL_FILE_MENU.get(),
        INFO_CTRL_PANEL_FILE_MENU_DESCRIPTION.get());
    menu.setMnemonic(KeyEvent.VK_F);
    JMenuItem menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_CONNECT_TO_SERVER_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        connectToServerClicked();
      }
    });
    menu.add(menuItem);

    if (!isMacOS())
    {
      menuItem = Utilities.createMenuItem(INFO_CTRL_PANEL_EXIT_MENU.get());
      menuItem.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          quitClicked();
        }
      });
      menu.add(menuItem);
    }
    return menu;
  }

  /**
   * Creates the View menu bar.
   * @return the View menu bar.
   */
  protected JMenu createViewMenuBar()
  {
    JMenu menu = Utilities.createMenu(INFO_CTRL_PANEL_VIEW_MENU.get(),
        INFO_CTRL_PANEL_HELP_VIEW_DESCRIPTION.get());
    menu.setMnemonic(KeyEvent.VK_V);
    JMenuItem menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_REFRESH_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        refreshOptionsClicked();
      }
    });
    menu.add(menuItem);
    return menu;
  }

  /** Specific method to be able to handle the Quit events sent from the COCOA menu of Mac OS. */
  private void setMacOSQuitHandler()
  {
    try
    {
      Class<? extends Object> applicationClass =
        Class.forName("com.apple.eawt.Application");
      Class<? extends Object> applicationListenerClass =
        Class.forName("com.apple.eawt.ApplicationListener");
      final Object  macApplication = applicationClass.getConstructor(
          (Class[])null).newInstance((Object[])null);
      InvocationHandler adapter = new InvocationHandler()
      {
        @Override
        public Object invoke (Object proxy, Method method, Object[] args)
        throws Throwable
        {
          Object event = args[0];
          if (method.getName().equals("handleQuit"))
          {
            quitClicked();

            // quitClicked will exit if we must exit
            Method setHandledMethod = event.getClass().getDeclaredMethod(
                "setHandled", new Class[] { boolean.class });
            setHandledMethod.invoke(event, new Object[] { Boolean.FALSE });
          }
          return null;
        }
      };
      Method addListenerMethod =
        applicationClass.getDeclaredMethod("addApplicationListener",
            new Class[] { applicationListenerClass });
      Object proxy = Proxy.newProxyInstance(MainMenuBar.class.getClassLoader(),
          new Class[] { applicationListenerClass }, adapter);
      addListenerMethod.invoke(macApplication, new Object[] { proxy });
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  /** The method called when the user clicks on 'Refresh Options'. */
  private void refreshOptionsClicked()
  {
    if (panel == null)
    {
      panel = new RefreshOptionsPanel();
      panel.setInfo(getInfo());
      dlg = new GenericDialog(
          Utilities.getFrame(MainMenuBar.this),
          panel);
      dlg.setModal(true);
      Utilities.centerGoldenMean(dlg,
          Utilities.getFrame(MainMenuBar.this));
    }
    dlg.setVisible(true);
    if (!panel.isCanceled())
    {
      getInfo().setPoolingPeriod(panel.getPoolingPeriod());
      getInfo().stopPooling();
      getInfo().startPooling();
    }
  }

  /** The method called when the user clicks on 'Connect to Server...'. */
  private void connectToServerClicked()
  {
    Set<String> runningTasks = new HashSet<>();
    for (Task task : getInfo().getTasks())
    {
      if (task.getState() == Task.State.RUNNING)
      {
        runningTasks.add(task.getTaskDescription().toString());
      }
    }
    boolean confirmed = true;
    if (!runningTasks.isEmpty())
    {
      String allTasks = Utilities.getStringFromCollection(runningTasks, "<br>");
      LocalizableMessage title = INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get();
      LocalizableMessage msg =
        INFO_CTRL_PANEL_RUNNING_TASKS_CHANGE_SERVER_CONFIRMATION_DETAILS.get(
            allTasks);
      confirmed = Utilities.displayConfirmationDialog(
          Utilities.getParentDialog(this), title, msg);
    }
    if (confirmed)
    {
      GenericDialog dlg =
        ControlCenterMainPane.getLocalOrRemoteDialog(getInfo());
      Utilities.centerGoldenMean(dlg,
          Utilities.getFrame(MainMenuBar.this));
      dlg.setVisible(true);
    }
  }
}
