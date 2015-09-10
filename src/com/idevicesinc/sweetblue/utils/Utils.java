package com.idevicesinc.sweetblue.utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.idevicesinc.sweetblue.BleDeviceConfig;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.util.Log;

/**
 * Some static utility methods that are probably not very useful outside the library.
 * See subclasses for more specific groups of utility methods.
 */
public class Utils
{
	static boolean requiresBonding(BluetoothGattCharacteristic characteristic)
	{
		return false;
	}
	
	private static final int FRACTION_DIGITS = 2;
	
	private static final DecimalFormat s_toFixedFormat = new DecimalFormat();
	{
		s_toFixedFormat.setMaximumFractionDigits(FRACTION_DIGITS);
		s_toFixedFormat.setMinimumFractionDigits(FRACTION_DIGITS);
	}

	public static boolean isLollipop()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
	}

	public static String toFixed(final double value)
	{
		return s_toFixedFormat.format(value);
	}

	public static int toBits(final BitwiseEnum ... enums)
	{
		int bits = 0x0;

		for( int i = 0; i < enums.length; i++ )
		{
			bits |= enums[i].bit();
		}

		return bits;
	}
	
	/**
	 * Returns true for certain Sony and Motorola products, which may have problems managing bonding state
	 * and so this method is used in {@link com.idevicesinc.sweetblue.BleDeviceConfig.DefaultBondFilter}. 
	 */ 
	public static boolean phoneHasBondingIssues()
	{
		return
			Utils.isManufacturer("sony")																		||
			Utils.isManufacturer("motorola") && (Utils.isProduct("ghost") || Utils.isProduct("victara"))		||
			Utils.isManufacturer("samsung") && (Utils.isProduct("degaswifiue"))									 ;
	}
	
	public static boolean isManufacturer(String manufacturer)
	{
		return Build.MANUFACTURER != null && Build.MANUFACTURER.equalsIgnoreCase(manufacturer);
	}
	
	public static boolean isProduct(String product)
	{
		return Build.PRODUCT != null && Build.PRODUCT.contains(product);
	}

	public static boolean isOnMainThread()
	{
		return Looper.getMainLooper().getThread() == Thread.currentThread();
	}

	public static void enforceMainThread()
	{
		if( !isOnMainThread() )
		{
			throw new WrongThreadException();
		}
	}

	public static boolean isSuccess(int gattStatus)
	{
		return gattStatus == 0;// || gattStatus == 1;
	}
	
	public static String normalizeDeviceName(String deviceName)
	{
		if( deviceName == null || deviceName.length() == 0 )  return "";
		
		String[] nameParts = deviceName.split("-");
		String consistentName = nameParts[0];
		consistentName = consistentName.toLowerCase();
		consistentName = consistentName.replace(" ", "_");
		
		return consistentName;
	}

	public static boolean contains(final Object[] uuids, final Object uuid)
	{
		for( int i = 0; i < uuids.length; i++ )
		{
			final Object ith = uuids[i];

			if( ith.equals(uuid) )
			{
				return true;
			}
		}

		return false;
	}

	public static boolean haveMatchingIds(List<UUID> advertisedIds, Collection<UUID> lookedForIds)
	{
		if(lookedForIds != null && !lookedForIds.isEmpty())
		{
			boolean match = false;

			for(int i = 0; i < advertisedIds.size(); i++)
			{
				if(lookedForIds.contains(advertisedIds.get(i)))
				{
					match = true;
					break;
				}
			}

			if(!match)
				return false;
		}

		return true;
	}

	public static byte[] hexStringToBytes(String string)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		for(int idx = 0; idx + 2 <= string.length(); idx += 2)
		{
			String hexStr = string.substring(idx, idx + 2);
			int intValue = Integer.parseInt(hexStr, 16);
			baos.write(intValue);
		}

		return baos.toByteArray();
	}

	public static List<byte[]> fileToBinaryDataList(Context context, String file, int offset)
	{
		List<byte[]> binaryData = new ArrayList<byte[]>();
		BufferedReader reader = null;

		try
		{
			InputStream stream = context.getAssets().open(file);

			String currentLine;
			reader = new BufferedReader(new InputStreamReader(stream));

			while((currentLine = reader.readLine()) != null)
			{
				String rawLine = currentLine.substring(1);
				//				Log.d("", ".");
				//				Log.d("", "rawLine:     " + rawLine);
				byte[] data = hexStringToBytes(rawLine);
				//				Log.d("", "data_before: " + bytesToHex(data));

				long data_1 = 0x0 | data[1];
				data_1 <<= 8;
				data_1 &= 0xff00;

				long data_2 = 0x0 | data[2];
				data_2 &= 0x00ff;

				long addr = data_1 + data_2;

				//				Log.d("", "addr:        "+addr);
				//				Log.d("", "offset:      "+offset);
				long type = data[3];
				type &= 0x00ff;
				//				Log.d("", "type:        "+type);
				if((type == 0) && (addr < offset))
				{
					continue;
				}

				// patch up address
				addr -= offset;
				data[1] = (byte) ((addr & 0xff00) >>> 8);
				data[2] = (byte) (addr & 0xff);

				//				Log.d("", "data_after: "+bytesToHex(data));
				//				Log.d("", ".");

				// Cut off checksum
				byte[] subBytes = subBytes(data, 0, data.length - 1);
				binaryData.add(subBytes);
			}
		}
		catch(IOException e)
		{
			return null;
		}
		finally
		{
			if(reader != null)
			{
				try
				{
					reader.close();
				}
				catch(IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		if(binaryData != null)
		{
			Collections.reverse(binaryData);
		}

		return binaryData;
	}

	public static byte[] subBytes(byte[] source, int sourceBegin, int sourceEnd)
	{
		byte[] destination = new byte[sourceEnd - sourceBegin];
		System.arraycopy(source, sourceBegin, destination, 0, sourceEnd - sourceBegin);
		return destination;
	}
	
	public static void memcpy(byte[] dest, byte[] source, int size, int destOffset, int sourceOffset)
	{
		for(int i = 0; i < size; i++)
		{
			dest[i+destOffset] = source[i+sourceOffset];
		}
	}

	public static void memcpy(byte[] dest, byte[] source, int size)
	{
		memcpy(dest, source, size, /*destOffset=*/0, /*destOffset=*/0);
	}

	public static void memset(byte[] data, byte value, int size)
	{
		for(int i = 0; i < size; i++)
		{
			data[i] = value;
		}
	}

	public static boolean memcmp(byte[] buffer1, byte[] buffer2, int size)
	{
		for(int i = 0; i < size; i++)
		{
			if(buffer1[i] != buffer2[i])
			{
				return false;
			}
		}

		return true;
	}

	public static int getIntValue(byte[] data)
	{
		//--- DRK > Have to pad it out from 3 to 4 bytes then flip byte endianness...not required in iOS version.
		byte[] data_padded = new byte[4];
		memcpy(data_padded, data, data.length);
		int value = ByteBuffer.wrap(data_padded).getInt();
		value = Integer.reverseBytes(value);

		return value;
	}
	
	public static String getStringValue(final byte[] data, final String charset)
	{
		String string = "";
		byte[] value = data;

		if(value != null && value.length > 0)
		{
			try
			{
				string = new String(value, charset);
			}
			catch(UnsupportedEncodingException e)
			{
				return "";
			}

			string = string.trim();
		}

		return string;
	}

	public static String getStringValue(final byte[] data)
	{
		return getStringValue(data, "UTF-8");
	}

	private static class FlagOnStyle extends CharacterStyle
	{
		@Override public void updateDrawState(TextPaint tp)
		{
			tp.setColor(0xFF006400);
		}
	};

	private static class FlagOffStyle extends CharacterStyle
	{
		@Override public void updateDrawState(TextPaint tp)
		{
			tp.setColor(0xFFFF0000);
			tp.setStrikeThruText(true);
		}
	};
	
	public static void reverseBytes(byte[] data)
	{
		for( int i = 0; i < data.length/2; i++ )
		{
			byte first = data[i];
			byte last = data[data.length-1-i];
			
			data[i] = last;
			data[data.length-1-i] = first;
		}
	}

	public static SpannableString makeStateString(State[] states, int stateMask)
	{
		String rawString = "";
		String spacer = "  ";

		for(int i = 0; i < states.length; i++)
		{
			if( states[i].isNull() )  continue;
			
			String name = ((Enum) states[i]).name();
			rawString += name + spacer;
		}

		SpannableString spannableString = new SpannableString(rawString);

		int position = 0;
		for(int i = 0; i < states.length; i++)
		{
			if( states[i].isNull() )  continue;
			
			String name = ((Enum) states[i]).name();

			if(states[i].overlaps(stateMask))
			{
				spannableString.setSpan(new FlagOnStyle(), position, position + name.length(), 0x0);
			}
			else
			{
				spannableString.setSpan(new FlagOffStyle(), position, position + name.length(), 0x0);
			}

			position += name.length() + spacer.length();
		}

		return spannableString;
	}
	
	public static boolean hasPermission(Context context, String permission)
	{
		int res = context.checkCallingOrSelfPermission(permission);
		
	    return (res == PackageManager.PERMISSION_GRANTED);
	}
	
	public static short unsignedByte(byte value)
	{
		return (short) (value & 0xff);
	}
	
	public static String toString(int mask, State[] values)
	{
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		
		boolean foundFirst = false;
		
		for( int i = 0; i < values.length; i++ )
		{
			if( values[i].overlaps(mask) )
			{
				if( foundFirst )
				{
					builder.append(", ");
				}
				else
				{
					foundFirst = true;
				}
				
				builder.append(values[i]);
			}
		}
		
		builder.append("]");
		
		return builder.toString();
	}
	
	public static int calcFullMask(final State[] values)
	{
		int mask = 0x0;
		
		for( int i = 0; i < values.length; i++ )
		{
			mask |= values[i].bit();
		}
		
		return mask;
	}
	
	public static String toString(Class<?> type, Object ... values)
	{
		StringBuilder builder = new StringBuilder();
		
		builder.append(type.getSimpleName());
		
		int length_highest = 0;
		for( int i = 0; i < values.length; i+=2 )
		{
			int length_ith = values[i].toString().length();
			
			if( length_ith > length_highest )
			{
				length_highest = length_ith;
			}
		}
		
		for( int i = 0; i < values.length; i+=2 )
		{
			builder.append("\n   ");
			
			final int length_ith = values[i].toString().length();
			final int spaceCount = length_highest - length_ith;
			
			builder.append(values[i]);
			
			for( int j = 0; j < spaceCount; j++ )
			{
				builder.append(" ");
			}
			builder.append(" = ");
			builder.append(values[i+1]);
		}
		
		return builder.toString();
	}

	public static byte[] shortToBytes(short l)
	{
		byte[] result = new byte[2];
		for( short i = 1; i >= 0; i-- )
		{
			result[i] = (byte) (l & 0xFF);
			l >>= 8;
		}
		return result;
	}

	public static short bytesToShort(byte[] b)
	{
		short result = 0;
		for( short i = 0; i < 2; i++ )
		{
			result <<= 8;
			result |= (b[i] & 0xFF);
		}

		return result;
	}

	public static byte boolToByte(final boolean value)
	{
		return (byte) (value ? 0x1 : 0x0);
	}

	public static byte[] intToBytes(int l)
	{
		byte[] result = new byte[4];
		for( int i = 3; i >= 0; i-- )
		{
			result[i] = (byte) (l & 0xFF);
			l >>= 8;
		}
		return result;
	}

	public static int bytesToInt(byte[] b)
	{
		int result = 0;
		for( int i = 0; i < 4; i++ )
		{
			result <<= 8;

			if( i < b.length )
			{
				result |= (b[i] & 0xFF);
			}
		}

		return result;
	}



	public static byte[] longToBytes(long l)
	{
		byte[] result = new byte[8];
		for( int i = 7; i >= 0; i-- )
		{
			result[i] = (byte) (l & 0xFF);
			l >>= 8;
		}
		return result;
	}

	public static long bytesToLong(byte[] b)
	{
		long result = 0;
		for( int i = 0; i < 8; i++ )
		{
			result <<= 8;
			result |= (b[i] & 0xFF);
		}
		return result;
	}
}
