package com.royalenfield.vehicleadvertiser

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.Arrays
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val BATTERY_SERVICE_UUID = UUID
        .fromString("0000180F-0000-1000-8000-00805f9b34fb")

    private val BATTERY_LEVEL_UUID = UUID
        .fromString("00002A19-0000-1000-8000-00805f9b34fb")

    private val CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID
        .fromString("00002901-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID
        .fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val INITIAL_BATTERY_LEVEL = 50
    private val BATTERY_LEVEL_MAX = 100
    private val BATTERY_LEVEL_DESCRIPTION = "The current charge level of a " +
            "battery. 100% represents fully charged while 0% represents fully discharged."

    private var mBatteryService: BluetoothGattService? = null
    private var mBatteryLevelCharacteristic: BluetoothGattCharacteristic? = null
    private var mBluetoothGattService: BluetoothGattService? = null
    private var mAdvData: AdvertiseData? = null
    private var mAdvScanResponse: AdvertiseData? = null
    private var mAdvSettings: AdvertiseSettings? = null
    private lateinit var mAdvertiser: BluetoothLeAdvertiser
    private lateinit var mGattServer: BluetoothGattServer
    private var mBluetoothManager: BluetoothManager? = null
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private val mBluetoothDevices: HashSet<BluetoothDevice>? = null

    private lateinit var mAdvStatus: TextView
    private var mConnectionStatus: TextView? = null
    private lateinit var mStartAdvertise: Button

    private val mAdvCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(
                "BLE_DEVICE_STATUS",
                "Not broadcasting: $errorCode"
            )
            val statusText: Int
            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> {
                    statusText = R.string.status_advertising
                    Log.w(
                        "BLE_DEVICE_STATUS",
                        "App was already advertising"
                    )
                }

                ADVERTISE_FAILED_DATA_TOO_LARGE -> statusText = R.string.status_advDataTooLarge
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> statusText =
                    R.string.status_advFeatureUnsupported

                ADVERTISE_FAILED_INTERNAL_ERROR -> statusText = R.string.status_advInternalError
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> statusText =
                    R.string.status_advTooManyAdvertisers

                else -> {
                    statusText = R.string.status_notAdvertising
                    Log.wtf(
                        "BLE_DEVICE_STATUS",
                        "Unhandled error: $errorCode"
                    )
                }
            }
            mAdvStatus!!.setText(statusText)
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.v("BLE_DEVICE_STATUS", "Broadcasting")
            mAdvStatus!!.setText(R.string.status_advertising)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)


        mAdvStatus = findViewById<TextView>(R.id.tv_info)
        mStartAdvertise = findViewById<Button>(R.id.bt_start)
        mConnectionStatus = findViewById<TextView>(R.id.tv_status)



        mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager!!.getAdapter()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        mBluetoothAdapter.setName("RoyalEnfieldEV");


        mBatteryLevelCharacteristic = BluetoothGattCharacteristic(
            BATTERY_LEVEL_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        mBatteryLevelCharacteristic!!.addDescriptor(
            getClientCharacteristicConfigurationDescriptor()
        )

        mBatteryLevelCharacteristic!!.addDescriptor(
            getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION)
        )

        mBatteryLevelCharacteristic!!.setValue(
            85,
            BluetoothGattCharacteristic.FORMAT_UINT8,  /* offset */0
        )

        mBatteryService = BluetoothGattService(
            BATTERY_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        mBatteryService!!.addCharacteristic(mBatteryLevelCharacteristic)


        mStartAdvertise.setOnClickListener {

            mBluetoothGattService = mBatteryService

            mAdvSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()
            mAdvData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(BATTERY_SERVICE_UUID))
                .build()
            mAdvScanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {


            }else{
                mGattServer = mBluetoothManager!!.openGattServer(this, mGattServerCallback)

                mGattServer.addService(mBluetoothGattService)

                if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {
                    mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser()
                    mAdvertiser.startAdvertising(mAdvSettings, mAdvData, mAdvScanResponse, mAdvCallback)
                } else {
                    mAdvStatus.setText(R.string.status_noLeAdv)
                }
            }

        }



    }


    private val mGattServerCallback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(device, status, newState)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        mBluetoothDevices?.add(device)
                        updateConnectedDevicesStatus()
                        Log.v(
                            "BLE_DEVICE_STATUS",
                            "Connected to device: " + device.address
                        )
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        mBluetoothDevices?.remove(device)
                        updateConnectedDevicesStatus()
                        Log.v(
                            "BLE_DEVICE_STATUS",
                            "Disconnected from device"
                        )
                    }
                } else {
                    mBluetoothDevices?.remove(device)
                    updateConnectedDevicesStatus()
                    // There are too many gatt errors (some of them not even in the documentation) so we just
                    // show the error to the user.
                    val errorMessage =
                        getString(R.string.status_errorWhenConnecting) + ": " + status
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                    Log.e(
                        "BLE_DEVICE_STATUS",
                        "Error when connecting: $status"
                    )
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice, requestId: Int, offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                Log.d(
                    "BLE_DEVICE_STATUS",
                    "Device tried to read characteristic: " + characteristic.uuid
                )
                Log.d(
                    "BLE_DEVICE_STATUS",
                    "Value: " + Arrays.toString(characteristic.value)
                )
                if (offset != 0) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    mGattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_INVALID_OFFSET,
                        offset,  /* value (optional) */
                        null
                    )
                    return
                }
                mGattServer!!.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.value
                )
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                super.onNotificationSent(device, status)
                Log.v(
                    "BLE_DEVICE_STATUS",
                    "Notification sent. Status: $status"
                )
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                super.onCharacteristicWriteRequest(
                    device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value
                )
                Log.v(
                    "BLE_DEVICE_STATUS",
                    "Characteristic Write request: " + Arrays.toString(value)
                )
                val status: Int =
                    writeCharacteristic(characteristic, offset, value)
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    mGattServer!!.sendResponse(
                        device, requestId, status,  /* No need to respond with an offset */
                        0,  /* No need to respond with a value */
                        null
                    )
                }
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice, requestId: Int,
                offset: Int, descriptor: BluetoothGattDescriptor
            ) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor)
                Log.d(
                    "BLE_DEVICE_STATUS",
                    "Device tried to read descriptor: " + descriptor.uuid
                )
                Log.d(
                    "BLE_DEVICE_STATUS",
                    "Value: " + Arrays.toString(descriptor.value)
                )
                if (offset != 0) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    mGattServer!!.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_INVALID_OFFSET,
                        offset,  /* value (optional) */
                        null
                    )
                    return
                }
                mGattServer!!.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    descriptor.value
                )
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                super.onDescriptorWriteRequest(
                    device, requestId, descriptor, preparedWrite, responseNeeded,
                    offset, value
                )
                Log.v(
                    "BLE_DEVICE_STATUS",
                    "Descriptor Write Request " + descriptor.uuid + " " + Arrays.toString(value)
                )
                var status = BluetoothGatt.GATT_SUCCESS
                if (descriptor.uuid === CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
                    val characteristic = descriptor.characteristic
                    val supportsNotifications = characteristic.properties and
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val supportsIndications = characteristic.properties and
                            BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (!(supportsNotifications || supportsIndications)) {
                        status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                    } else if (value.size != 2) {
                        status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                    } else if (Arrays.equals(
                            value,
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        )
                    ) {
                        status = BluetoothGatt.GATT_SUCCESS
                        notificationsDisabled(characteristic)
                        descriptor.value = value
                    } else if (supportsNotifications &&
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    ) {
                        status = BluetoothGatt.GATT_SUCCESS
                        notificationsEnabled(
                            characteristic,
                            false /* indicate */
                        )
                        descriptor.value = value
                    } else if (supportsIndications &&
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    ) {
                        status = BluetoothGatt.GATT_SUCCESS
                       notificationsEnabled(
                            characteristic,
                            true /* indicate */
                        )
                        descriptor.value = value
                    } else {
                        status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                    }
                } else {
                    status = BluetoothGatt.GATT_SUCCESS
                    descriptor.value = value
                }
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    mGattServer!!.sendResponse(
                        device, requestId, status,  /* No need to respond with offset */
                        0,  /* No need to respond with a value */
                        null
                    )
                }
            }
        }


    private fun updateConnectedDevicesStatus() {
       if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }else{
           val message =  (getString(R.string.status_devicesConnected) + " "
                   + mBluetoothManager!!.getConnectedDevices(BluetoothGattServer.GATT).size)
           runOnUiThread { mConnectionStatus!!.text = message }
       }

    }

    fun getClientCharacteristicConfigurationDescriptor(): BluetoothGattDescriptor? {
        val descriptor = BluetoothGattDescriptor(
          CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        descriptor.value = byteArrayOf(0, 0)
        return descriptor
    }

    fun getCharacteristicUserDescriptionDescriptor(defaultValue: String): BluetoothGattDescriptor? {
        val descriptor = BluetoothGattDescriptor(
            CHARACTERISTIC_USER_DESCRIPTION_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        try {
            descriptor.value = defaultValue.toByteArray(charset("UTF-8"))
        } finally {
            return descriptor
        }
    }

    fun notificationsEnabled(characteristic: BluetoothGattCharacteristic?, indicate: Boolean) {
        throw UnsupportedOperationException("Method notificationsEnabled not overridden")
    }
    fun notificationsDisabled(characteristic: BluetoothGattCharacteristic?) {
        throw java.lang.UnsupportedOperationException("Method notificationsDisabled not overridden")
    }
    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic?,
        offset: Int,
        value: ByteArray?
    ): Int {
        throw java.lang.UnsupportedOperationException("Method writeCharacteristic not overridden")
    }


    private fun ensureBleFeaturesAvailable() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetoothNotSupported, Toast.LENGTH_LONG).show()
            Log.e(
               "BLE_DEVICE_STATUS",
                "Bluetooth not supported"
            )
            finish()
        } else if (!mBluetoothAdapter.isEnabled) {
            // Make sure bluetooth is enabled.
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(
                enableBtIntent,
                1
            )
        }
    }
}

