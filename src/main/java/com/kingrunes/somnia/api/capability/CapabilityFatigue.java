package com.kingrunes.somnia.api.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

public class CapabilityFatigue
{
    @CapabilityInject(IFatigue.class)
    public static Capability<IFatigue> FATIGUE_CAPABILITY = null;

    public static void register()
    {
        CapabilityManager.INSTANCE.register(IFatigue.class, new FatigueCapabilityStorage(), Fatigue::new);
    }
}
