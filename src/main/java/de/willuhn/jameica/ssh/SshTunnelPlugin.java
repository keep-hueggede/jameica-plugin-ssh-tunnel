/**********************************************************************
 *
 * Copyright (c) 2026 Olaf Willuhn
 * All rights reserved.
 * 
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details. 
 *
 **********************************************************************/

package de.willuhn.jameica.ssh;

import de.willuhn.jameica.plugin.AbstractPlugin;
import de.willuhn.logging.Logger;

/**
 * Basis-Klasse des Jameica-Plugins "ssh-tunnel".
 *
 * <p>Sie erzeugt den {@link SshTunnelService} und baut den SSH-Tunnel beim
 * Start von Jameica auf, sofern er aktiviert ist. Beim Beenden von Jameica
 * wird der Tunnel wieder abgebaut.
 */
public class SshTunnelPlugin extends AbstractPlugin
{
  private static SshTunnelService service = null;

  /**
   * @see de.willuhn.jameica.plugin.Plugin#init()
   */
  public void init()
  {
    service = new SshTunnelService();

    if (!service.isEnabled())
    {
      Logger.info("ssh tunnel is disabled");
      return;
    }

    try
    {
      service.start();
    }
    catch (Exception e)
    {
      Logger.error("unable to start ssh tunnel",e);
    }
  }

  /**
   * @see de.willuhn.jameica.plugin.Plugin#shutDown()
   */
  public void shutDown()
  {
    if (service != null)
      service.stop();
  }

  /**
   * Liefert die Instanz des SSH-Tunnel-Service.
   * @return die Instanz des Service oder {@code null}, wenn das Plugin noch
   * nicht initialisiert wurde.
   */
  public static SshTunnelService getService()
  {
    return service;
  }
}
