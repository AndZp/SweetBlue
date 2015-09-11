package com.idevicesinc.sweetblue;

import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.idevicesinc.sweetblue.BleDevice.BondListener;
import com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener;
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener;
import com.idevicesinc.sweetblue.BleDeviceConfig.ReconnectRequestFilter.ReconnectRequestEvent;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener.DiscoveryEvent;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener.LifeCycle;
import com.idevicesinc.sweetblue.annotations.Nullable;
import com.idevicesinc.sweetblue.annotations.Immutable;
import com.idevicesinc.sweetblue.annotations.Nullable.Prevalence;
import com.idevicesinc.sweetblue.utils.*;

/**
 * Provides a number of options to (optionally) pass to {@link BleDevice#setConfig(BleDeviceConfig)}.
 * This class is also a super class of {@link BleManagerConfig}, which you can pass
 * to {@link BleManager#get(Context, BleManagerConfig)} or {@link BleManager#setConfig(BleManagerConfig)} to set default base options for all devices at once.
 * For all options in this class, you may set the value to <code>null</code> when passed to {@link BleDevice#setConfig(BleDeviceConfig)}
 * and the value will then be inherited from the {@link BleManagerConfig} passed to {@link BleManager#get(Context, BleManagerConfig)}.
 * Otherwise, if the value is not <code>null</code> it will override any option in the {@link BleManagerConfig}.
 * If an option is ultimately <code>null</code> (<code>null</code> when passed to {@link BleDevice#setConfig(BleDeviceConfig)}
 * *and* {@link BleManager#get(Context, BleManagerConfig)}) then it is interpreted as <code>false</code> or {@link Interval#DISABLED}.
 * <br><br>
 * TIP: You can use {@link Interval#DISABLED} instead of <code>null</code> to disable any time-based options, for code readability's sake.
 * <br><br>
 * TIP: You can use {@link #newNulled()} (or {@link #nullOut()}) then only set the few options you want for {@link BleDevice#setConfig(BleDeviceConfig)}.
 */
public class BleDeviceConfig extends BleNodeConfig implements Cloneable
{
	public static final double DEFAULT_MINIMUM_SCAN_TIME				= 5.0;
	public static final int DEFAULT_RUNNING_AVERAGE_N					= 10;
	public static final double DEFAULT_SCAN_KEEP_ALIVE					= DEFAULT_MINIMUM_SCAN_TIME*2.5;
	
	/**
	 * Default value for {@link #rssiAutoPollRate}.
	 */
	public static final double DEFAULT_RSSI_AUTO_POLL_RATE				= 10.0;
	
	/**
	 * Default fallback value for {@link #rssi_min}.
	 */
	public static final int DEFAULT_RSSI_MIN							= -120;
	
	/**
	 * Default fallback value for {@link #rssi_max}.
	 */
	public static final int DEFAULT_RSSI_MAX							= -30;

	/**
	 * Default value for {@link #defaultTxPower}.
	 */
	public static final int DEFAULT_TX_POWER							= -50;
	
	/**
	 * @deprecated Use {@link BleStatuses#BOND_FAIL_REASON_NOT_APPLICABLE}.
	 */
	@Deprecated
	public static final int BOND_FAIL_REASON_NOT_APPLICABLE				= BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE;
	
	/**
	 * Default is <code>true</code> - some devices can only reliably become {@link BleDeviceState#BONDED} while {@link BleDeviceState#DISCONNECTED},
	 * so this option controls whether the library will internally change any bonding flow dictated by {@link #bondFilter} when a bond fails and try
	 * to bond again the next time the device is {@link BleDeviceState#DISCONNECTED}.
	 * <br><br>
	 * NOTE: This option was added after noticing this behavior with the Samsung Tab 4 running 4.4.4.
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean tryBondingWhileDisconnected					= true;
	
	/**
	 * Default is <code>true</code> - controls whether any bonding issues worked around if {@link #tryBondingWhileDisconnected} is <code>true</code> are remembered on disk
	 * (through {@link SharedPreferences}) so that bonding is as stable as possible across application sessions. 
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean tryBondingWhileDisconnected_manageOnDisk		= true;

	/**
	 * Default is <code>true</code> - controls whether changes to a device's name through {@link BleDevice#setName(String)} are remembered on disk through
	 * {@link SharedPreferences}. If true, this means calls to {@link com.idevicesinc.sweetblue.BleDevice#getName_override()} will return the same thing
	 * even across app restarts.
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean saveNameChangesToDisk						= true;
	
	/**
	 * Default is <code>true</code> - whether to automatically get services immediately after a {@link BleDevice} is
	 * {@link BleDeviceState#CONNECTED}. Currently this is the only way to get a device's services.
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean autoGetServices								= true;
	
	/**
	 * Default is <code>false</code>se - if true and you call {@link BleDevice#startPoll(UUID, Interval, BleDevice.ReadWriteListener)}
	 * or {@link BleDevice#startChangeTrackingPoll(UUID, Interval, BleDevice.ReadWriteListener)()} with identical
	 * parameters then two identical polls would run which would probably be wasteful and unintentional.
	 * This option provides a defense against that situation.
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean allowDuplicatePollEntries					= false;
	
	/**
	 * Default is <code>false</code> - {@link BleDevice#getAverageReadTime()} and {@link BleDevice#getAverageWriteTime()} can be 
	 * skewed if the peripheral you are connecting to adjusts its maximum throughput for OTA firmware updates and the like.
	 * Use this option to let the library know whether you want read/writes to factor in while {@link BleDeviceState#PERFORMING_OTA}.
	 * 
	 * @see BleDevice#getAverageReadTime()
	 * @see BleDevice#getAverageWriteTime() 
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean includeOtaReadWriteTimesInAverage			= false;
	
	/**
	 * Default is <code>true</code> - controls whether {@link BleManager} will keep a device in active memory when it goes {@link BleManagerState#OFF}.
	 * If <code>false</code> then a device will be purged and you'll have to do {@link BleManager#startScan()} again to discover devices
	 * if/when {@link BleManager} goes back {@link BleManagerState#ON}.
	 * <br><br>
	 * NOTE: if this flag is true for {@link BleManagerConfig} passed to {@link BleManager#get(Context, BleManagerConfig)} then this
	 * applies to all devices.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Boolean retainDeviceWhenBleTurnsOff					= true;
	
	/**
	 * Default is <code>true</code> - only applicable if {@link #retainDeviceWhenBleTurnsOff} is also true. If {@link #retainDeviceWhenBleTurnsOff}
	 * is false then devices will be undiscovered when {@link BleManager} goes {@link BleManagerState#OFF} regardless.
	 * <br><br>
	 * NOTE: See NOTE for {@link #retainDeviceWhenBleTurnsOff} for how this applies to {@link BleManagerConfig}. 
	 * 
	 * @see #retainDeviceWhenBleTurnsOff
	 * @see #autoReconnectDeviceWhenBleTurnsBackOn
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Boolean undiscoverDeviceWhenBleTurnsOff				= true;
	
	/**
	 * Default is <code>true</code> - if devices are kept in memory for a {@link BleManager#turnOff()}/{@link BleManager#turnOn()} cycle
	 * (or a {@link BleManager#reset()}) because {@link #retainDeviceWhenBleTurnsOff} is <code>true</code>, then a {@link BleDevice#connect()}
	 * will be attempted for any devices that were previously {@link BleDeviceState#CONNECTED}.
	 * <br><br>
	 * NOTE: See NOTE for {@link #retainDeviceWhenBleTurnsOff} for how this applies to {@link BleManagerConfig}.
	 * 
	 * @see #retainDeviceWhenBleTurnsOff
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Boolean autoReconnectDeviceWhenBleTurnsBackOn 		= true;
	
	/**
	 * Default is <code>true</code> - controls whether the {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent} behind a device going {@link BleDeviceState#DISCONNECTED}
	 * is saved to and loaded from disk so that it can be restored across app sessions, undiscoveries, and BLE
	 * {@link BleManagerState#OFF}->{@link BleManagerState#ON} cycles. This uses Android's {@link SharedPreferences} so does not require
	 * any extra permissions. The main advantage of this is the following scenario: User connects to a device through your app,
	 * does what they want, kills the app, then opens the app sometime later. {@link BleDevice#getLastDisconnectIntent()} returns
	 * {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent#UNINTENTIONAL}, which lets you know that you can probably automatically connect to this device without user confirmation.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Boolean manageLastDisconnectOnDisk					= true;
	
	/**
	 * Default is <code>true</code> - controls whether a {@link BleDevice} is placed into an in-memory cache when it becomes {@link BleDeviceState#UNDISCOVERED}.
	 * If <code>true</code>, subsequent calls to {@link BleManager.DiscoveryListener#onEvent(BleManager.DiscoveryListener.DiscoveryEvent)} with
	 * {@link LifeCycle#DISCOVERED} (or calls to {@link BleManager#newDevice(String)}) will return the cached {@link BleDevice} instead of creating a new one.
	 * <br><br>
	 * The advantages of caching are:<br>
	 * <ul>
	 * <li>Slightly better performance at the cost of some retained memory, especially in situations where you're frequently discovering and undiscovering many devices.
	 * <li>Resistance to future stack failures that would otherwise mean missing data like {@link BleDevice#getAdvertisedServices()} for future discovery events.
	 * <li>More resistant to potential "user error" of retaining devices in app-land after BleManager undiscovery.
	 * <ul><br>
	 * This is kept as an option in case there's some unforeseen problem with devices being cached for a certain application.
	 * 
	 * See also {@link #minScanTimeNeededForUndiscovery}.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Boolean cacheDeviceOnUndiscovery						= true;
	
	/**
	 * Default is <code>true</code> - controls whether {@link BleDevice.ConnectionFailListener.Status#BONDING_FAILED} is capable of
	 * inducing {@link ConnectionFailListener#onEvent(com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.ConnectionFailEvent)}
	 * while a device is {@link BleDeviceState#CONNECTING_OVERALL}.
	 */
	@Nullable(Prevalence.NORMAL)
	public Boolean bondingFailFailsConnection					= true;
	
	/**
	 * Default is <code>false</code> - whether to use <code>BluetoothGatt.refresh()</code> right before service discovery.
	 * This method is not in the public Android API, so its use is disabled by default. You may find it useful to enable
	 * if your remote device is routinely changing its gatt service profile. This method call supposedly clears a cache
	 * that would otherwise prevent changes from being discovered.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Boolean useGattRefresh								= false;
	
	/**
	 * Default is {@link #DEFAULT_MINIMUM_SCAN_TIME} seconds - Undiscovery of devices must be
	 * approximated by checking when the last time was that we discovered a device,
	 * and if this time is greater than {@link #undiscoveryKeepAlive} then the device is undiscovered. However a scan
	 * operation must be allowed a certain amount of time to make sure it discovers all nearby devices that are
	 * still advertising. This is that time in seconds.
	 * <br><br>
	 * Use {@link Interval#DISABLED} to disable undiscovery altogether.
	 * 
	 * @see BleManager.DiscoveryListener#onEvent(DiscoveryEvent)
	 * @see #undiscoveryKeepAlive
	 */
	@Nullable(Prevalence.NORMAL)
	public Interval	minScanTimeNeededForUndiscovery				= Interval.secs(DEFAULT_MINIMUM_SCAN_TIME);
	
	/**
	 * Default is {@link #DEFAULT_SCAN_KEEP_ALIVE} seconds - If a device exceeds this amount of time since its
	 * last discovery then it is a candidate for being undiscovered.
	 * The default for this option attempts to accommodate the worst Android phones (BLE-wise), which may make it seem
	 * like it takes a long time to undiscover a device. You may want to configure this number based on the phone or
	 * manufacturer. For example, based on testing, in order to make undiscovery snappier the Galaxy S5 could use lower times.
	 * <br><br>
	 * Use {@link Interval#DISABLED} to disable undiscovery altogether.
	 * 
	 * @see BleManager.DiscoveryListener#onEvent(DiscoveryEvent)
	 * @see #minScanTimeNeededForUndiscovery
	 */
	@Nullable(Prevalence.NORMAL)
	public Interval	undiscoveryKeepAlive						= Interval.secs(DEFAULT_SCAN_KEEP_ALIVE);
	
	/**
	 * Default is {@link #DEFAULT_RSSI_AUTO_POLL_RATE} - The rate at which a {@link BleDevice} will automatically poll for its {@link BleDevice#getRssi()} value
	 * after it's {@link BleDeviceState#CONNECTED}. You may also use {@link BleDevice#startRssiPoll(Interval, ReadWriteListener)} for more control and feedback.
	 */
	@Nullable(Prevalence.NORMAL)
	public Interval rssiAutoPollRate							= Interval.secs(DEFAULT_RSSI_AUTO_POLL_RATE);
	
	/**
	 * Default is {@link #DEFAULT_RUNNING_AVERAGE_N} - The number of historical write times that the library should keep track of when calculating average time.
	 * 
	 * @see BleDevice#getAverageWriteTime()
	 * @see #nForAverageRunningReadTime
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Integer		nForAverageRunningWriteTime				= DEFAULT_RUNNING_AVERAGE_N;
	
	/**
	 * Default is {@link #DEFAULT_RUNNING_AVERAGE_N} - Same thing as {@link #nForAverageRunningWriteTime} but for reads.
	 * 
	 * @see BleDevice#getAverageWriteTime()
	 * @see #nForAverageRunningWriteTime
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Integer		nForAverageRunningReadTime				= DEFAULT_RUNNING_AVERAGE_N;
	
	/**
	 * Default is {@link #DEFAULT_TX_POWER} - this value is used if we can't establish a device's calibrated transmission power from the device itself,
	 * either through its scan record or by reading the standard characteristic. To get a good value for this on a per-remote-device basis
	 * experimentally, simply run a sample app and use {@link BleDevice#startRssiPoll(Interval, ReadWriteListener)} and spit {@link BleDevice#getRssi()}
	 * to your log. The average value of {@link BleDevice#getRssi()} at one meter away is the value you should use for this config option.
	 * 
	 * @see BleDevice#getTxPower()
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Nullable(Prevalence.NORMAL)
	public Integer		defaultTxPower							= DEFAULT_TX_POWER;
	
	/**
	 * Default is {@link #DEFAULT_RSSI_MIN} - the estimated minimum value for {@link BleDevice#getRssi()}.
	 */
	@Nullable(Prevalence.NORMAL)
	public Integer		rssi_min								= DEFAULT_RSSI_MIN;
	
	/**
	 * Default is {@link #DEFAULT_RSSI_MAX} - the estimated maximum value for {@link BleDevice#getRssi()}.
	 */
	@Nullable(Prevalence.NORMAL)
	public Integer		rssi_max								= DEFAULT_RSSI_MAX;
	
	/**
	 * Default is instance of {@link DefaultBondFilter}.
	 * 
	 * @see BondFilter
	 */
	@Nullable(Prevalence.NORMAL)
	public BondFilter bondFilter											= new DefaultBondFilter();
	
	/**
	 * Default is an instance of {@link DefaultReconnectRequestFilter} - set an implementation here to
	 * have fine control over reconnect behavior while {@link BleDevice} is {@link BleDeviceState#RECONNECTING_LONG_TERM}.
	 * This is basically how often and how long
	 * the library attempts to reconnect to a device that for example may have gone out of range. Set this variable to
	 * <code>null</code> if reconnect behavior isn't desired. If not <code>null</code>, your app may find
	 * {@link BleManagerConfig#manageCpuWakeLock} useful in order to force the app/phone to stay awake while attempting a reconnect.
	 * 
	 * @see BleManagerConfig#manageCpuWakeLock
	 * @see ReconnectRequestFilter
	 * @see DefaultReconnectRequestFilter
	 */
	@Nullable(Prevalence.NORMAL)
	public ReconnectRequestFilter reconnectRequestFilter_longTerm			= new DefaultReconnectRequestFilter(DefaultReconnectRequestFilter.LONG_TERM_ATTEMPT_RATE);
	
	/**
	 * Default is an instance of {@link DefaultReconnectPersistFilter} created using {@link DefaultReconnectPersistFilter#LONG_TERM_TIMEOUT} - set an implementation here to
	 * optionally stop any ongoing {@link BleDeviceState#RECONNECTING_LONG_TERM} loop. 
	 */
	@Nullable(Prevalence.NORMAL)
	public ReconnectPersistFilter reconnectPersistFilter_longTerm			= new DefaultReconnectPersistFilter(DefaultReconnectPersistFilter.LONG_TERM_TIMEOUT);
	
	/**
	 * Same as {@link #reconnectRequestFilter_longTerm} but for {@link BleDeviceState#RECONNECTING_SHORT_TERM}.  
	 */
	@Nullable(Prevalence.NORMAL)
	public ReconnectRequestFilter reconnectRequestFilter_shortTerm			= new DefaultReconnectRequestFilter(DefaultReconnectRequestFilter.SHORT_TERM_ATTEMPT_RATE);
	
	/**
	 * Same as {@link #reconnectPersistFilter_longTerm} but for {@link BleDeviceState#RECONNECTING_SHORT_TERM}.
	 * Default is an instance of {@link DefaultReconnectPersistFilter} that will return {@link ReconnectPersistFilter.Please#stopRetrying()} after
	 * {@link DefaultReconnectPersistFilter#SHORT_TERM_TIMEOUT} exceeds.
	 */
	@Nullable(Prevalence.NORMAL)
	public ReconnectPersistFilter reconnectPersistFilter_shortTerm			= new DefaultReconnectPersistFilter(DefaultReconnectPersistFilter.SHORT_TERM_TIMEOUT);

	/**
	 * As of now there are two main default uses for this class...
	 * <br><br>
	 * The first is that in at least some cases it's not possible to determine beforehand whether a given characteristic requires
	 * bonding, so implementing this interface on {@link BleManagerConfig#bondFilter} lets the app give
	 * a hint to the library so it can bond before attempting to read or write an encrypted characteristic.
	 * Providing these hints lets the library handle things in a more deterministic and optimized fashion, but is not required.
	 * <br><br>
	 * The second is that some android devices have issues when it comes to bonding. So far the worst culprits
	 * are certain Sony and Motorola phones, so if it looks like {@link Build#MANUFACTURER}
	 * is either one of those, {@link DefaultBondFilter} is set to unbond upon discoveries and disconnects.
	 * Please look at the source of {@link DefaultBondFilter} for the most up-to-date spec.
	 * The problem seems to be associated with mismanagement of pairing keys by the OS and
	 * this brute force solution seems to be the only way to smooth things out.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@com.idevicesinc.sweetblue.annotations.Lambda
	public static interface BondFilter
	{
		/**
		 * Just a dummy subclass of {@link BleDevice.StateListener.StateEvent} so that this gets auto-imported for implementations of {@link BondFilter}.
		 */
		@com.idevicesinc.sweetblue.annotations.Advanced
		public static class StateChangeEvent extends BleDevice.StateListener.StateEvent
		{
			StateChangeEvent(BleDevice device, int oldStateBits, int newStateBits, int intentMask, int gattStatus)
			{
				super(device, oldStateBits, newStateBits, intentMask, gattStatus);
			}
		}

		/**
		 * An enumeration of the type of characteristic operation for a {@link CharacteristicEvent}.
		 */
		@com.idevicesinc.sweetblue.annotations.Advanced
		public static enum CharacteristicEventType
		{
			/**
			 * Started from {@link BleDevice#read(UUID, ReadWriteListener)}, {@link BleDevice#startPoll(UUID, Interval, ReadWriteListener)}, etc.
			 */
			READ,

			/**
			 * Started from {@link BleDevice#write(UUID, byte[], ReadWriteListener)} or overloads.
			 */
			WRITE,

			/**
			 * Started from {@link BleDevice#enableNotify(UUID, ReadWriteListener)} or overloads.
			 */
			ENABLE_NOTIFY;
		}

		/**
		 * Struct passed to {@link BondFilter#onEvent(CharacteristicEvent)}.
		 */
		@com.idevicesinc.sweetblue.annotations.Advanced
		@Immutable
		public static class CharacteristicEvent
		{
			/**
			 * Returns the {@link BleDevice} in question.
			 */
			public BleDevice device(){  return m_device;  }
			private final BleDevice m_device;

			/**
			 * Returns the {@link UUID} of the characteristic in question.
			 */
			public UUID charUuid(){  return m_uuid;  }
			private final UUID m_uuid;

			/**
			 * Returns the type of characteristic operation, read, write, etc.
			 */
			public CharacteristicEventType type(){  return m_type;  }
			private final CharacteristicEventType m_type;

			CharacteristicEvent(BleDevice device, UUID uuid, CharacteristicEventType type)
			{
				m_device = device;
				m_uuid = uuid;
				m_type = type;
			}

			@Override public String toString()
			{
				return Utils_String.toString
				(
					this.getClass(),
					"device",		device().getName_debug(),
					"charUuid",		device().getManager().getLogger().charName(charUuid()),
					"type",			type()
				);
			}
		}

		/**
		 * Return value for the various interface methods of {@link BondFilter}.
		 * Use static constructor methods to create instances.
		 */
		@com.idevicesinc.sweetblue.annotations.Advanced
		@Immutable
		public static class Please
		{
			private final Boolean m_bond;
			private final BondListener m_bondListener;

			Please(Boolean bond, BondListener listener)
			{
				m_bond = bond;
				m_bondListener = listener;
			}

			Boolean bond_private()
			{
				return m_bond;
			}

			BondListener listener()
			{
				return m_bondListener;
			}

			/**
			 * Device should be bonded if it isn't already.
			 */
			public static Please bond()
			{
				return new Please(true, null);
			}

			/**
			 * Returns {@link #bond()} if the given condition holds <code>true</code>, {@link #doNothing()} otherwise.
			 */
			public static Please bondIf(boolean condition)
			{
				return condition ? bond() : doNothing();
			}

			/**
			 * Same as {@link #bondIf(boolean)} but lets you pass a {@link BondListener} as well.
			 */
			public static Please bondIf(boolean condition, BondListener listener)
			{
				return condition ? bond(listener) : doNothing();
			}

			/**
			 * Same as {@link #bond()} but lets you pass a {@link BondListener} as well.
			 */
			public static Please bond(BondListener listener)
			{
				return new Please(true, listener);
			}

			/**
			 * Device should be unbonded if it isn't already.
			 */
			public static Please unbond()
			{
				return new Please(false, null);
			}

			/**
			 * Returns {@link #bond()} if the given condition holds <code>true</code>, {@link #doNothing()} otherwise.
			 */
			public static Please unbondIf(boolean condition)
			{
				return condition ? unbond() : doNothing();
			}

			/**
			 * Device's bond state should not be affected.
			 */
			public static Please doNothing()
			{
				return new Please(null, null);
			}
		}

		/**
		 * Called after a device undergoes a change in its {@link BleDeviceState}.
		 */
		Please onEvent(StateChangeEvent e);

		/**
		 * Called immediately before reading, writing, or enabling notification on a characteristic.
		 */
		Please onEvent(CharacteristicEvent e);
	}

	/**
	 * Default implementation of {@link BondFilter} that unbonds for certain phone models upon discovery and disconnects.
	 * See further explanation in documentation for {@link BondFilter}.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Immutable
	public static class DefaultBondFilter implements BondFilter
	{
		/**
		 * Forwards {@link Utils#phoneHasBondingIssues()}. Override to make this <code>true</code> for more (or fewer) phones.
		 */
		public boolean phoneHasBondingIssues()
		{
			return Utils.phoneHasBondingIssues();
		}

		@Override public Please onEvent(StateChangeEvent e)
		{
			if( phoneHasBondingIssues() )
			{
				if( !e.device().is(BleDeviceState.BONDING) )
				{
					return Please.unbondIf( e.didEnterAny(BleDeviceState.DISCOVERED, BleDeviceState.DISCONNECTED) );
				}
			}

			return Please.doNothing();
		}

		@Override public Please onEvent(CharacteristicEvent e)
		{
			return Please.doNothing();
		}
	}

	/**
	 * Abstract base class for {@link ReconnectRequestEvent} and {@link BleDeviceConfig.ReconnectPersistFilter.ReconnectPersistEvent} just to
	 * tie their APIs together and statically ensure that they are consistent.
	 */
	@com.idevicesinc.sweetblue.annotations.Advanced
	@Immutable
	public static abstract class ReconnectRelatedEvent
	{
		/**
		 * The device that is currently {@link BleDeviceState#RECONNECTING_LONG_TERM}.
		 */
		public abstract BleDevice device();

		/**
		 * The number of times a reconnect attempt has failed so far.
		 */
		public abstract int failureCount();

		/**
		 * The total amount of time since the device went {@link BleDeviceState#DISCONNECTED} and we started the reconnect loop.
		 */
		public abstract Interval totalTimeReconnecting();

		/**
		 * The previous {@link Interval} returned from {@link ReconnectRequestFilter#onEvent(ReconnectRequestEvent)}, or {@link Interval#ZERO}
		 * for the first invocation.
		 */
		public abstract Interval previousDelay();

		/**
		 * Returns the more detailed information about why the connection failed. This is passed to {@link BleDevice.ConnectionFailListener#onEvent(com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.ConnectionFailEvent)}
		 * before the call is made to {@link ReconnectRequestFilter#onEvent(ReconnectRequestEvent)}. For the first call to {@link ReconnectRequestFilter#onEvent(ReconnectRequestEvent)},
		 * right after a spontaneous disconnect occurred, the connection didn't fail, so {@link BleDevice.ConnectionFailListener.ConnectionFailEvent#isNull()} will return <code>true</code>.
		 */
		public abstract ConnectionFailListener.ConnectionFailEvent connectionFailInfo();

		/**
		 * Returns whether this event is related to {@link BleDeviceState#RECONNECTING_SHORT_TERM}.
		 */
		public boolean shortTerm()
		{
			return device().is(BleDeviceState.INITIALIZED);
		}

		/**
		 * Returns whether this event is related to {@link BleDeviceState#RECONNECTING_LONG_TERM}.
		 */
		public boolean longTerm()
		{
			return !shortTerm();
		}

		/**
		 * Returns <i>only</i> either {@link BleDeviceState#RECONNECTING_SHORT_TERM} or {@link BleDeviceState#RECONNECTING_LONG_TERM}
		 * depending on what "reconnecting" state this event is associated with.
		 */
		public BleDeviceState state()
		{
			return shortTerm() ? BleDeviceState.RECONNECTING_SHORT_TERM : BleDeviceState.RECONNECTING_LONG_TERM;
		}

		@Override public String toString()
		{
			return Utils_String.toString
			(
				this.getClass(),
				"device",					device().getName_debug(),
				"failureCount",				failureCount(),
				"totalTimeReconnecting",	totalTimeReconnecting(),
				"previousDelay",			previousDelay(),
				"state",					state()
			);
		}
	}

	/**
	 * An optional interface you can implement on {@link BleDeviceConfig#reconnectRequestFilter_longTerm}
	 * and/or {@link BleDeviceConfig#reconnectRequestFilter_shortTerm} to control reconnection behavior.
	 *
	 * @see #reconnectRequestFilter_longTerm
	 * @see #reconnectRequestFilter_shortTerm
	 * @see DefaultReconnectRequestFilter
	 */
	@com.idevicesinc.sweetblue.annotations.Lambda
	public static interface ReconnectRequestFilter
	{
		/**
		 * Struct passed to {@link BleDeviceConfig.ReconnectRequestFilter#onEvent(BleDeviceConfig.ReconnectRequestFilter.ReconnectRequestEvent)} to aid in making a decision.
		 */
		@Immutable
		public static class ReconnectRequestEvent extends ReconnectRelatedEvent
		{
			@Override public BleDevice device(){  return m_device;  }
			private final BleDevice m_device;

			@Override public int failureCount(){  return m_failureCount;  }
			private final int m_failureCount;

			@Override public Interval totalTimeReconnecting(){  return m_totalTimeReconnecting;  }
			private final Interval m_totalTimeReconnecting;

			@Override public Interval previousDelay(){  return m_previousDelay;  }
			private final Interval m_previousDelay;

			@Override public ConnectionFailListener.ConnectionFailEvent connectionFailInfo(){  return m_connectionFailInfo;  }
			private final ConnectionFailListener.ConnectionFailEvent m_connectionFailInfo;

			ReconnectRequestEvent(BleDevice device, int failureCount, Interval totalTimeReconnecting, Interval previousDelay, ConnectionFailListener.ConnectionFailEvent connectionFailInfo)
			{
				this.m_device = device;
				this.m_failureCount = failureCount;
				this.m_totalTimeReconnecting = totalTimeReconnecting;
				this.m_previousDelay = previousDelay;
				this.m_connectionFailInfo = connectionFailInfo;
			}
		}

		/**
		 * Return value for {@link ReconnectRequestFilter#onEvent(ReconnectRequestEvent)}. Use static constructor methods to create instances.
		 */
		@Immutable
		public static class Please
		{
			static final Interval INSTANTLY = Interval.ZERO;
			static final Interval STOP = Interval.DISABLED;

			private final Interval m_interval;

			private Please(Interval interval)
			{
				m_interval = interval;
			}

			Interval getInterval()
			{
				return m_interval;
			}

			/**
			 * Return this from {@link BleDeviceConfig.ReconnectRequestFilter#onEvent(BleDeviceConfig.ReconnectRequestFilter.ReconnectRequestEvent)} to instantly reconnect.
			 */
			public static Please retryInstantly()
			{
				return new Please(INSTANTLY);
			}

			/**
			 * Return this from {@link BleDeviceConfig.ReconnectRequestFilter#onEvent(BleDeviceConfig.ReconnectRequestFilter.ReconnectRequestEvent)} to stop a reconnect attempt loop.
			 * Note that {@link BleDevice#disconnect()} will also stop any ongoing reconnect loop.
			 */
			public static Please stopRetrying()
			{
				return new Please(STOP);
			}

			/**
			 * Return this from {@link BleDeviceConfig.ReconnectRequestFilter#onEvent(BleDeviceConfig.ReconnectRequestFilter.ReconnectRequestEvent)} to retry after the given amount of time.
			 */
			public static Please retryIn(Interval interval)
			{
				return new Please(interval != null ? interval : INSTANTLY);
			}
		}

		/**
		 * Called for every connection failure while device is {@link BleDeviceState#RECONNECTING_LONG_TERM}.
		 * Use the static methods of {@link Please} as return values to stop reconnection ({@link Please#stopRetrying()}), try again
		 * instantly ({@link Please#retryInstantly()}), or after some amount of time {@link Please#retryIn(Interval)}.
		 */
		Please onEvent(final ReconnectRequestEvent e);
	}

	/**
	 * Default implementation of {@link ReconnectRequestFilter} that uses {@link ReconnectRequestFilter.Please#retryInstantly()} for the
	 * first reconnect attempt, and from then on uses the {@link Interval} rate passed to the constructor
	 *
	 */
	public static class DefaultReconnectRequestFilter implements ReconnectRequestFilter
	{
		public static final Please DEFAULT_INITIAL_RECONNECT_DELAY = Please.retryInstantly();
		public static final Interval LONG_TERM_ATTEMPT_RATE = Interval.secs(3.0);
		public static final Interval SHORT_TERM_ATTEMPT_RATE = Interval.secs(1.0);

		private final Please m_please;

		public DefaultReconnectRequestFilter(Interval reconnectRate)
		{
			m_please = Please.retryIn(reconnectRate);
		}

		@Override public Please onEvent(ReconnectRequestEvent e)
		{
			if( e.failureCount() == 0 )
			{
				return DEFAULT_INITIAL_RECONNECT_DELAY;
			}
			else
			{
				return m_please;
			}
		}
	}

	/**
	 * Set an instance on {@link BleDeviceConfig#reconnectPersistFilter_longTerm} and/or {@link BleDeviceConfig#reconnectPersistFilter_shortTerm}.
	 */
	@com.idevicesinc.sweetblue.annotations.Lambda
	public static interface ReconnectPersistFilter
	{
		/**
		 * Struct passed to {@link ReconnectPersistFilter#onEvent(ReconnectPersistEvent)}.
		 */
		@Immutable
		public static class ReconnectPersistEvent extends ReconnectRelatedEvent
		{
			@Override public BleDevice device(){  return m_device;  }
			private BleDevice m_device;

			@Override public int failureCount(){  return m_failureCount;  }
			private int m_failureCount;

			@Override public Interval totalTimeReconnecting(){  return m_totalTimeReconnecting;  }
			private Interval m_totalTimeReconnecting;

			@Override public Interval previousDelay(){  return m_previousDelay;  }
			private Interval m_previousDelay;

			@Override public ConnectionFailListener.ConnectionFailEvent connectionFailInfo(){  return m_connectionFailInfo;  }
			private ConnectionFailListener.ConnectionFailEvent m_connectionFailInfo;

			void init(BleDevice device, int failureCount, Interval totalTimeReconnecting, Interval previousDelay, ConnectionFailListener.ConnectionFailEvent connectionFailInfo)
			{
				this.m_device = device;
				this.m_failureCount = failureCount;
				this.m_totalTimeReconnecting = totalTimeReconnecting;
				this.m_previousDelay = previousDelay;
				this.m_connectionFailInfo = connectionFailInfo;
			}
		}

		/**
		 * Return an instance of this from {@link ReconnectPersistFilter#onEvent(ReconnectPersistEvent)} using
		 * {@link ReconnectPersistFilter.Please#stopRetrying()} or {@link ReconnectPersistFilter.Please#persist()}.
		 */
		@Immutable
		public static class Please
		{
			private static final Please PERSIST		= new Please(true);
			private static final Please STOP		= new Please(false);

			private final boolean m_persist;

			private Please(boolean persist)
			{
				m_persist = persist;
			}

			boolean shouldPersist()
			{
				return m_persist;
			}

			/**
			 * Indicates that the {@link BleDevice} should stop {@link BleDeviceState#RECONNECTING_LONG_TERM} or
			 * {@link BleDeviceState#RECONNECTING_SHORT_TERM}.
			 */
			public static Please stopRetrying()
			{
				return STOP;
			}

			/**
			 * Returns {@link #stopRetrying()} if the condition holds, {@link #persist()} otherwise.
			 */
			public static Please stopRetryingIf(boolean condition)
			{
				return condition ? stopRetrying() : persist();
			}

			/**
			 * Indicates that the {@link BleDevice} should keep {@link BleDeviceState#RECONNECTING_LONG_TERM} or
			 * {@link BleDeviceState#RECONNECTING_SHORT_TERM}.
			 */
			public static Please persist()
			{
				return PERSIST;
			}

			/**
			 * Returns {@link #persist()} if the condition holds, {@link #stopRetrying()} otherwise.
			 */
			public static Please persistIf(boolean condition)
			{
				return condition ? persist() : stopRetrying();
			}
		}

		/**
		 * Called periodically while {@link BleDeviceState#RECONNECTING_LONG_TERM} or {@link BleDeviceState#RECONNECTING_SHORT_TERM}
		 * are active. Currently the rate at which this is called is the same as {@link BleManagerConfig#autoUpdateRate}. If {@link BleManagerConfig#autoUpdateRate}
		 * is {@link Interval#DISABLED} (it should rarely be) then it will be called however often {@link BleManager#update(double)} is called.
		 */
		Please onEvent(final ReconnectPersistEvent e);
	}

	/**
	 * Default implementation of {@link ReconnectPersistFilter} that returns {@link ReconnectPersistFilter.Please#persist()} for as long
	 * as the reconnect process has taken less time than the {@link Interval} passed to
	 * {@link BleDeviceConfig.DefaultReconnectPersistFilter#onEvent(BleDeviceConfig.ReconnectPersistFilter.ReconnectPersistEvent)}.
	 * <br><br>
	 * NOTE: This filter will not kill the reconnect process if we're past the timeout but are {@link BleDeviceState#CONNECTED} and
	 * going through the final steps of {@link BleDeviceState#CONNECTING_OVERALL} like {@link BleDeviceState#DISCOVERING_SERVICES},
	 * {@link BleDeviceState#AUTHENTICATING}, or {@link BleDeviceState#INITIALIZING}.
	 */
	public static class DefaultReconnectPersistFilter implements ReconnectPersistFilter
	{
		public static final Interval SHORT_TERM_TIMEOUT = Interval.FIVE_SECS;
		public static final Interval LONG_TERM_TIMEOUT = Interval.mins(5);

		private final Interval m_timeout;

		public DefaultReconnectPersistFilter(Interval timeout)
		{
			m_timeout = timeout != null ? timeout : Interval.INFINITE;
		}

		@Override public Please onEvent(final ReconnectPersistEvent e)
		{
			final boolean definitelyPersist =
					BleDeviceState.CONNECTING_OVERALL.overlaps(e.device().getNativeStateMask()) &&
					BleDeviceState.CONNECTED.overlaps(e.device().getNativeStateMask())			;;

			//--- DRK > We don't interrupt if we're in the middle of connecting
			//---		but this will be the last attempt if it fails.
			if( definitelyPersist )
			{
				return Please.persist();
			}
			else
			{
				return Please.persistIf(e.totalTimeReconnecting().lt(m_timeout));
			}
		}
	}
	
	/**
	 * Creates a {@link BleDeviceConfig} with all default options set. See each member of this class
	 * for what the default options are set to. Consider using {@link #newNulled()} also.
	 */
	public BleDeviceConfig()
	{
	}
	
	/**
	 * Convenience method that returns a nulled out {@link BleDeviceConfig}, which is useful
	 * when using {@link BleDevice#setConfig(BleDeviceConfig)} to only override a few options
	 * from {@link BleManagerConfig} passed to {@link BleManager#get(Context, BleManagerConfig)}
	 * or {@link BleManager#setConfig(BleManagerConfig)}.
	 */
	public static BleDeviceConfig newNulled()
	{
		BleDeviceConfig config = new BleDeviceConfig();
		config.nullOut();
		
		return config;
	}

	@Override protected BleManagerConfig clone()
	{
		return (BleManagerConfig) super.clone();
	}
}
