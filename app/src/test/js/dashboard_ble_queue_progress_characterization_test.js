'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');

const ble = read('src/main/java/com/buttonbox/ble/BleManager.kt');
const diagnostics = read('src/main/java/com/buttonbox/ble/BleWriteQueueDiagnostics.kt');

function section(source, start, end) {
  const startIndex = source.indexOf(start);
  assert.ok(startIndex >= 0, `Missing section start: ${start}`);
  const endIndex = source.indexOf(end, startIndex + start.length);
  assert.ok(endIndex > startIndex, `Missing section end after ${start}: ${end}`);
  return source.slice(startIndex, endIndex);
}

function includesAll(source, values, label) {
  for (const value of values) {
    assert.ok(source.includes(value), `${label} missing: ${value}`);
  }
}

includesAll(ble, [
  'private val queueTrace = BleWriteQueueTrace',
  'private val buttonWriteQueue = InstrumentedBleWriteQueue(',
  'BleWriteQueueKind.BUTTON,',
  'private val varRequestQueue = InstrumentedBleWriteQueue(',
  'BleWriteQueueKind.VARIABLE_REQUEST,',
  'private val gpsDataQueue = InstrumentedBleWriteQueue(',
  'BleWriteQueueKind.GPS_DATA,',
  'private var isWritingButton = false',
  'private var isWritingVarRequest = false',
  'private var isWritingGpsData = false'
], 'BLE queue declarations');

includesAll(diagnostics, [
  'private val delegate = ArrayDeque<ByteArray>()',
  'fun offer(data: ByteArray): Boolean',
  'fun poll(): ByteArray?',
  'fun isEmpty(): Boolean = delegate.isEmpty()',
  'fun clear()',
  'trace.onEnqueue(kind, data.size)',
  'trace.onPoll(kind, data.size)',
  'trace.onClear(kind)'
], 'instrumented FIFO wrapper');

for (const forbidden of [
  'ArrayBlockingQueue',
  'LinkedBlockingQueue',
  'MAX_BUTTON_QUEUE',
  'MAX_VAR_REQUEST_QUEUE',
  'MAX_GPS_QUEUE',
  'private val sharedWriteQueue',
  'private var isWritingBle',
  'processNextWriteQueue()'
]) {
  assert.ok(!ble.includes(forbidden) && !diagnostics.includes(forbidden),
    `Unexpected bounded/shared queue construct: ${forbidden}`);
}

const buttonProcess = section(
  ble,
  'private fun processButtonQueue()',
  'private fun processVarRequestQueue()'
);
const varProcess = section(
  ble,
  'private fun processVarRequestQueue()',
  '/** Send button mask with queuing for rapid presses. */'
);
const gpsProcess = section(
  ble,
  'private fun processGpsDataQueue()',
  '/** Send GPS data to ESP32 for CAN transmission. */'
);

const queueSpecs = [
  {
    name: 'button',
    source: buttonProcess,
    queue: 'buttonWriteQueue',
    flag: 'isWritingButton',
    kind: 'BleWriteQueueKind.BUTTON'
  },
  {
    name: 'variable request',
    source: varProcess,
    queue: 'varRequestQueue',
    flag: 'isWritingVarRequest',
    kind: 'BleWriteQueueKind.VARIABLE_REQUEST'
  },
  {
    name: 'GPS',
    source: gpsProcess,
    queue: 'gpsDataQueue',
    flag: 'isWritingGpsData',
    kind: 'BleWriteQueueKind.GPS_DATA'
  }
];

for (const spec of queueSpecs) {
  includesAll(spec.source, [
    `if (${spec.flag} || ${spec.queue}.isEmpty()) return`,
    `val data = ${spec.queue}.poll() ?: return`,
    `return noteQueuePreconditionDrop(${spec.kind}, "characteristic")`,
    `return noteQueuePreconditionDrop(${spec.kind}, "gatt")`,
    'characteristic.value = data',
    'characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE',
    'val accepted = gatt.writeCharacteristic(characteristic)',
    `${spec.flag} = accepted`,
    `noteQueueSynchronousResult(${spec.kind}, accepted)`
  ], `${spec.name} processor`);

  const poll = spec.source.indexOf(`${spec.queue}.poll()`);
  const characteristic = spec.source.indexOf('val characteristic = ');
  const write = spec.source.indexOf('gatt.writeCharacteristic(characteristic)');
  const result = spec.source.indexOf(`noteQueueSynchronousResult(${spec.kind}, accepted)`);
  assert.ok(poll >= 0 && poll < characteristic && characteristic < write && write < result,
    `${spec.name} production order changed`);
  assert.ok(!spec.source.slice(poll).includes(`${spec.queue}.offer(data)`),
    `${spec.name} processor unexpectedly requeues an item`);
}

const writeCallback = section(
  ble,
  'override fun onCharacteristicWrite(',
  'private fun parseVariablePacket(data: ByteArray)'
);
includesAll(writeCallback, [
  'noteQueueCompletion(BleWriteQueueKind.BUTTON, status)',
  'isWritingButton = false',
  'processButtonQueue()',
  'noteQueueCompletion(BleWriteQueueKind.VARIABLE_REQUEST, status)',
  'isWritingVarRequest = false',
  'processVarRequestQueue()',
  'noteQueueCompletion(BleWriteQueueKind.GPS_DATA, status)',
  'isWritingGpsData = false',
  'processGpsDataQueue()'
], 'write completion callback');
assert.ok(!writeCallback.includes('if (status'),
  'Completion status may be observed but must not yet change queue progression');

const buttonSend = section(
  ble,
  '/** Send button mask with queuing for rapid presses. */',
  '/** Request a variable value from ECU, queued for rapid requests. */'
);
includesAll(buttonSend, [
  'buttonWriteQueue.offer(',
  'if (!isWritingButton) processButtonQueue()',
  'return true'
], 'button enqueue');

const variableSend = section(
  ble,
  '/** Request a variable value from ECU, queued for rapid requests. */',
  'private fun processGpsDataQueue()'
);
includesAll(variableSend, [
  'varRequestQueue.offer(data)',
  'varRequestQueue.offer(data.array())',
  'if (!isWritingVarRequest) processVarRequestQueue()',
  'return true'
], 'variable request enqueue');

const gpsSend = section(
  ble,
  '/** Send GPS data to ESP32 for CAN transmission. */',
  '/** Send a single ADC value to ECU. */'
);
includesAll(gpsSend, [
  'gpsDataQueue.offer(data)',
  'val data = LegacyGpsPayloadMapper.encodePackedEntry(varHash, packedValue)',
  'gpsDataQueue.offer(LegacyGpsPayloadMapper.encodeFloatEntries(entries))',
  'if (!isWritingGpsData) processGpsDataQueue()',
  'return true'
], 'GPS enqueue');

const clear = section(
  ble,
  'private fun clearCharacteristicsAndQueues()',
  'private fun ensureQueueDiagnosticsOwner()'
);
includesAll(clear, [
  'buttonWriteQueue.clear()',
  'varRequestQueue.clear()',
  'gpsDataQueue.clear()',
  'isWritingButton = false',
  'isWritingVarRequest = false',
  'isWritingGpsData = false'
], 'queue teardown');

includesAll(diagnostics, [
  'state.highWaterItems',
  'state.highWaterBytes',
  'state.synchronousAccepted',
  'state.synchronousRejected',
  'state.completionSuccess',
  'state.completionFailure',
  'state.clearedQueuedItems',
  'state.clearedInFlightItems',
  'state.lostItems',
  'maximumSimultaneousInFlight',
  'overlapEvents',
  'inFlightStartedElapsedMs',
  'lastInFlightDurationMs',
  'maxInFlightDurationMs',
  'InFlightAgeMs',
  'val completedBytes = state.inFlightBytes.coerceAtLeast(0)',
  'state.lostBytes += completedBytes.toLong()',
  'durationMs=$duration',
  'COUNTER_PUBLICATION_INTERVAL_MS = 500L',
  'if (!forceNextCounterPublication && !intervalElapsed) return emptyMap()',
  'OVERLAP_EVENT_SAMPLE_INTERVAL = 100L'
], 'queue diagnostics coverage');

includesAll(ble, [
  'LifecycleDiagnostics.registerManager(',
  'val counters = queueTrace.counterValues()',
  'publishQueueCounters(counters)',
  'LifecycleDiagnostics.setCounter(ownerId, counter, value)',
  'handler.postDelayed(',
  'BleWriteQueueTrace.COUNTER_PUBLICATION_INTERVAL_MS',
  'closeQueueDiagnostics("manual disconnect")'
], 'lifecycle diagnostics publication');

const publication = section(
  ble,
  'private fun publishQueueDiagnostics(signal: BleWriteQueueSignal?)',
  'private fun noteQueuePreconditionDrop('
);
includesAll(publication, [
  'if (counters.isNotEmpty())',
  'handler.removeCallbacks(queueCounterPublishRunnable)',
  'else if (!queueCounterPublishScheduled)',
  'queueCounterPublishScheduled = true',
  'queueCounterPublishRunnable',
  'private fun publishQueueCountersNow()',
  'private fun publishQueueCounters(counters: Map<String, Long>)'
], 'coalesced diagnostics publication');

assert.strictEqual((ble.match(/gatt\.writeCharacteristic\(characteristic\)/g) || []).length, 3,
  'The three legacy queues must remain independently dispatched');

for (const forbidden of ['writeTune', 'burnTune', 'calibrationWrite', 'firmwareFlash']) {
  assert.ok(![ble, diagnostics].some(source => source.includes(forbidden)),
    `forbidden write path added: ${forbidden}`);
}

console.log('Legacy BLE queue/progress characterization and observability contract passed');
console.log(JSON.stringify({
  queues: 3,
  bounded: false,
  globalSerialization: false,
  productionProgressionChanged: false,
  queueDepthAndHighWaterInstrumented: true,
  synchronousAcceptanceInstrumented: true,
  completionStatusInstrumented: true,
  inFlightDurationInstrumented: true,
  clearAndOverlapInstrumented: true,
  counterPublicationIntervalMs: 500
}));
