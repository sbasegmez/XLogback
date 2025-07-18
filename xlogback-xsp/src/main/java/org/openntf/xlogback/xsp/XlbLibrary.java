package org.openntf.xlogback.xsp;

import com.ibm.xsp.library.AbstractXspLibrary;
import org.openntf.xlogback.xsp.plugin.XspPlugin;

public class XlbLibrary extends AbstractXspLibrary {

    public XlbLibrary() {
    }

    @Override
    public String getLibraryId() {
        return XspPlugin.class.getPackage().getName() + ".library";
    }

    @Override
    public String getPluginId() {
        return XspPlugin.getContext().getBundle().getSymbolicName();
    }

    @Override
    public String[] getDependencies() {
        return new String[]{
        };
    }

    @Override
    public String[] getFacesConfigFiles() {
        return new String[]{
            "META-INF/xlb-faces-config.xml",
        };
    }

    @Override
    public String[] getXspConfigFiles() {
        return new String[]{
            "META-INF/xlb.xsp-config",
        };
    }

}
