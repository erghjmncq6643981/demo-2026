import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inspectSource, inspectPom } from './check-engineering-governance.mjs';

test('new files cannot exceed the size boundary and reviewed files cannot regrow', () => {
  assert.equal(inspectSource('client/features/call/new.ts', 'line\n'.repeat(601)).findings.length, 1);
  assert.equal(inspectSource('console/views/CdrReportView.vue', 'line\n'.repeat(842)).findings.length, 1);
  assert.equal(inspectSource('server/call/NewService.java', 'line\n'.repeat(800)).findings.length, 0);
  assert.equal(inspectSource('admin/com/chandler/fcc/admin/flow/application/FlowStudioService.java', 'line\n'.repeat(801)).findings.length, 1);
});

test('common and SDK source boundaries are checked even before an SDK gets its first contract', () => {
  assert.equal(inspectSource('common/Shared.java', 'import com.chandler.fcc.server.call.Call;').findings.length, 1);
  assert.equal(inspectSource('server-sdk/App.java', '@SpringBootApplication class App {}').findings.length, 1);
  assert.equal(inspectSource('admin-sdk/Boot.java', 'public static void main(String[] args) {}').findings.length, 1);
  assert.equal(inspectSource('server-sdk/Client.java', 'public interface Client {}').findings.length, 0);
});

test('POM dependencies cannot bypass the source import boundary', () => {
  const dependency = artifact => `<dependency><groupId>com.chandler</groupId><artifactId>${artifact}</artifactId></dependency>`;
  assert.equal(inspectPom('fcc-common', dependency('fcc-admin')).length, 1);
  assert.equal(inspectPom('fcc-admin', dependency('fcc-server')).length, 1);
  assert.equal(inspectPom('fcc-server', dependency('fcc-server-starter')).length, 1);
  assert.equal(inspectPom('fcc-server-starter', dependency('fcc-server')).length, 1);
  assert.equal(inspectPom('fcc-admin-starter', dependency('fcc-common')).length, 0);
  assert.equal(inspectPom('fcc-admin', dependency('fcc-common')).length, 0);
  assert.equal(inspectPom('fcc-common', `<!-- ${dependency('fcc-server')} -->`).length, 0);
});

test('shells cannot absorb transports and modules cannot import each other', () => {
  assert.equal(inspectSource('client/App.vue', "import { client } from './api/client';").findings.length, 1);
  assert.equal(inspectSource('server/call/Service.java', 'import com.chandler.fcc.admin.service.AgentService;').findings.length, 1);
  assert.equal(inspectSource('admin/Service.java', 'import com.chandler.fcc.server.call.Service;').findings.length, 1);
});

test('identity stays opaque while durations can be numeric', () => {
  assert.equal(inspectSource('client/features/call/new.ts', 'Number(row.callId)').findings.length, 1);
  assert.equal(inspectSource('client/features/call/new.ts', 'Number(row.durationSeconds)').findings.length, 0);
});
