/*
 * Bluegiga’s Bluetooth Smart Android SW for Bluegiga BLE modules
 * Contact: support@bluegiga.com.
 *
 * This is free software distributed under the terms of the MIT license reproduced below.
 *
 * Copyright (c) 2013, Bluegiga Technologies
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files ("Software")
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF 
 * ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A  PARTICULAR PURPOSE.
 */
package com.bluegiga.BLEDemo.datamodel;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import pl.polidea.treeview.AbstractTreeViewAdapter;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bluegiga.BLEDemo.BluetoothLeService;
import com.bluegiga.BLEDemo.CharacteristicActivity;
import com.bluegiga.BLEDemo.R;
import com.bluegiga.BLEDemo.datamodel.xml.Characteristic;
import com.bluegiga.BLEDemo.datamodel.xml.Service;

//ServiceCharacteristicAdapter - used to build up TreeView component in ServiceCharacteristicActivity
public class ServiceCharacteristicAdapter extends AbstractTreeViewAdapter<UUID> {

    private TreeBuilder<UUID> treeBuilder;
    private HashMap<UUID, BluetoothGattService> services;
    private HashMap<UUID, BluetoothGattCharacteristic> characteristics;
    private Device device;

    public ServiceCharacteristicAdapter(Activity activity, TreeStateManager<UUID> treeStateManager,
            TreeBuilder<UUID> treeBuilder, int numberOfLevels, HashMap<UUID, BluetoothGattService> services,
            Device device) {
        super(activity, treeStateManager, numberOfLevels);

        this.treeBuilder = treeBuilder;
        this.services = services;
        this.device = device;
        this.characteristics = new HashMap<UUID, BluetoothGattCharacteristic>();
        resetTreeView();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getNewChildView(TreeNodeInfo<UUID> treeNodeInfo) {
        LinearLayout viewLayout;

        if (treeNodeInfo.getLevel() == 0) {
            viewLayout = (LinearLayout) getActivity().getLayoutInflater().inflate(R.layout.list_item_service, null);
        } else {
            viewLayout = (LinearLayout) getActivity().getLayoutInflater().inflate(R.layout.list_item_characteristic,
                    null);
        }
        return updateView(viewLayout, treeNodeInfo);
    }

    @Override
    public View updateView(View view, TreeNodeInfo<UUID> treeNodeInfo) {
        final LinearLayout viewLayout = (LinearLayout) view;
        if (treeNodeInfo.getLevel() == 0) {
            final TextView serviceNameView = (TextView) viewLayout.findViewById(R.id.serviceName);
            final TextView serviceUuidView = (TextView) viewLayout.findViewById(R.id.serviceUuid);
            Service service = Engine.getInstance().getService(treeNodeInfo.getId());
            if (service != null) {
                serviceNameView.setText(service.getName().trim());
            } else {
                serviceNameView.setText(getActivity().getText(R.string.unknown_service));
            }
            serviceUuidView.setText(getActivity().getText(R.string.uuid) + " 0x"
                    + Common.convert128to16UUID(treeNodeInfo.getId().toString()));
        } else {
            final TextView charactNameView = (TextView) viewLayout.findViewById(R.id.characteristicName);
            final TextView charactUuidView = (TextView) viewLayout.findViewById(R.id.characteristicUuid);
            final TextView charactPropertiesView = (TextView) viewLayout.findViewById(R.id.characteristicProperties);

            Characteristic charact = Engine.getInstance().getCharacteristic(treeNodeInfo.getId());
            if (charact != null) {
                charactNameView.setText(charact.getName().trim());
            } else {
                charactNameView.setText(getActivity().getText(R.string.unknown_characteristic));
            }
            charactUuidView.setText(getActivity().getText(R.string.uuid) + " 0x"
                    + Common.convert128to16UUID(treeNodeInfo.getId().toString()));

            BluetoothGattCharacteristic bluetoothCharact = characteristics.get(treeNodeInfo.getId());
            charactPropertiesView.setText(getActivity().getText(R.string.properties_big_case) + " "
                    + Common.getProperties(getActivity(), bluetoothCharact.getProperties()));
        }

        return viewLayout;
    }

    @Override
    public void handleItemClick(final View view, final Object id) {
        final UUID uuidId = (UUID) id;
        final TreeNodeInfo<UUID> info = getManager().getNodeInfo(uuidId);
        if (info.isWithChildren()) {
            super.handleItemClick(view, id);
        } else if (info.getLevel() == 0) { // if user clicked on service

            resetTreeView();

            // sort services by uuids
            BluetoothGattService service = services.get(uuidId);
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            Collections.sort(characteristics, new Comparator<BluetoothGattCharacteristic>() {

                @Override
                public int compare(BluetoothGattCharacteristic lhs, BluetoothGattCharacteristic rhs) {
                    return lhs.getUuid().compareTo(rhs.getUuid());
                }

            });

            // add characteristic items to service
            for (BluetoothGattCharacteristic charact : characteristics) {
                this.characteristics.put(charact.getUuid(), charact);
                treeBuilder.addRelation(uuidId, charact.getUuid());
            }

        } else { // if user clicked on characteristic
            Engine.getInstance().setLastCharacteristic(characteristics.get(uuidId));
            Intent intent = new Intent(getActivity(), CharacteristicActivity.class);
            intent.putExtra(BluetoothLeService.DEVICE_ADDRESS, device.getAddress());
            getActivity().startActivity(intent);
        }
    }

    // Clears whole UI and adds only services
    private void resetTreeView() {
        treeBuilder.clear();
        characteristics.clear();
        SortedSet<UUID> keys = new TreeSet<UUID>(services.keySet());
        for (UUID uuid : keys) {
            treeBuilder.addRelation(null, uuid);
        }
    }

}
