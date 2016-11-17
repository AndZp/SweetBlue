package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.utils.BleStatuses;

final class P_ReconnectManager
{

    private final BleDevice mDevice;
    private int mReconnectTries;
    private int mMaxReconnecTries;
    private boolean mPoppedLock = true;


    public P_ReconnectManager(BleDevice device)
    {
        mDevice = device;
        mMaxReconnecTries = mDevice.getConfig().reconnectionTries;
    }

    final void setMaxReconnectTries(int tries)
    {
        mMaxReconnecTries = tries;
    }

    final boolean shouldFail()
    {
        boolean fail = mReconnectTries >= mMaxReconnecTries;
        if (fail)
        {
            if (mReconnectTries > 0)
            {
                mDevice.getManager().popWakeLock();
                mPoppedLock = true;
            }
            mDevice.stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BleDeviceState.RECONNECTING_SHORT_TERM, false);
        }
        return fail;
    }

    final void reconnect(int gattStatus)
    {
        if (mReconnectTries == 0)
        {
            mPoppedLock = false;
            mDevice.getManager().pushWakeLock();
        }
        mReconnectTries++;
        mDevice.stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, gattStatus, BleDeviceState.CONNECTED, false, BleDeviceState.RECONNECTING_SHORT_TERM, true);
        mDevice.connect();
    }

    final void reset()
    {
        mReconnectTries = 0;
        if (!mPoppedLock)
        {
            mDevice.getManager().popWakeLock();
        }
    }

}
