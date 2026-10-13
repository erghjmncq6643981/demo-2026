import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const roots = [
  ['common', resolve(repository, 'fcc-common/src/main/java')],
  ['server', resolve(repository, 'fcc-server/src/main/java')],
  ['admin', resolve(repository, 'fcc-admin/src/main/java')],
  ['client', resolve(repository, '../chandler26-fcc-client-web/src')],
  ['console', resolve(repository, '../chandler26-fcc-admin-web/src')],
  ['server-sdk', resolve(repository, 'fcc-server-starter/src/main/java'), true],
  ['admin-sdk', resolve(repository, 'fcc-admin-starter/src/main/java'), true],
];

// Reviewed legacy surfaces may not grow. New Java files cap at 800, Vue/TS at 600.
const reviewedBudgets = {
  'console/views/GroupManageView.vue': 869,
  'console/views/CdrReportView.vue': 841,
  'console/views/OrgReportView.vue': 768,
  'console/views/AgentManageView.vue': 728,
  'console/features/groups/composables/useGroupManagement.ts': 723,
  'console/views/IvrFlowView.vue': 653,
};

/** Inspect a production source without changing files or connecting to services. */
export function inspectSource(key, source) {
  const lines = source.trimEnd().split('\n').length;
  const budget = reviewedBudgets[key] ?? (key.endsWith('.java') ? 800 : 600);
  const findings = [];
  if (lines > budget) findings.push(`${key}: ${lines} lines exceeds reviewed budget ${budget}`);
  if (/^(client|console)\/App\.vue$/.test(key) && /from\s+['"][^'"]*(?:\/api\/|\/services\/)/.test(source)) {
    findings.push(`${key}: shell must not own REST, WebSocket or SIP transport`);
  }
  if (/^console\/views\/(AgentManageView|MonitorView|CdrReportView)\.vue$/.test(key)
      && /from\s+['"][^'"]*\/api\//.test(source)) {
    findings.push(`${key}: governed view must dispatch feature use cases`);
  }
  if (key.endsWith('.java') && /import com\.chandler\.fcc\.(?:admin|server)\.starter\./.test(source)) {
    findings.push(`${key}: executable services must not depend on starter implementation`);
  }
  if (/^server\//.test(key) && /import com\.chandler\.fcc\.admin\./.test(source)) {
    findings.push(`${key}: server must not import admin implementation`);
  }
  if (/^admin\//.test(key) && /import com\.chandler\.fcc\.server\./.test(source)) {
    findings.push(`${key}: admin must not import server implementation`);
  }
  if (/^common\//.test(key) && /import\s+com\.chandler\.fcc\.(?:admin|server)\./.test(source)) {
    findings.push(`${key}: common must not import executable services or SDKs`);
  }
  if (/^(?:server|admin)-sdk\//.test(key)
      && /(?:\bstatic\s+void\s+main\s*\(|@(?:SpringBootApplication|RestController|Controller|Service|Repository|Mapper)\b|import\s+com\.chandler\.fcc\.(?:admin|server)\.(?!starter\b))/.test(source)) {
    findings.push(`${key}: SDK must not contain executable or implementation code`);
  }
  if (!key.endsWith('.java') && /(?:Number|parseInt|parseFloat)\([^\n)]*(?:\.callId|\.agentId|\.recordingId|\.ctrlId)\b/.test(source)) {
    findings.push(`${key}: backend identifiers must remain opaque strings`);
  }
  return { lines, budget, findings };
}

/** Check declared module dependencies; inherited/external dependency resolution remains Maven's job. */
export function inspectPom(module, source) {
  const findings = [];
  const xml = source.replace(/<!--[\s\S]*?-->/g, '');
  for (const match of xml.matchAll(/<dependency>\s*([\s\S]*?)<\/dependency>/g)) {
    const group = match[1].match(/<groupId>\s*([^<]+?)\s*<\/groupId>/)?.[1];
    const artifact = match[1].match(/<artifactId>\s*([^<]+?)\s*<\/artifactId>/)?.[1];
    if (group !== 'com.chandler' || !artifact?.startsWith('fcc-')) continue;
    const forbidden = module === 'fcc-common'
      || (module.endsWith('-starter') && ['fcc-server', 'fcc-admin', 'fcc-server-starter', 'fcc-admin-starter'].includes(artifact))
      || (['fcc-server', 'fcc-admin'].includes(module) && artifact !== 'fcc-common');
    if (forbidden) findings.push(`${module}/pom.xml: forbidden product dependency ${artifact}`);
  }
  return findings;
}

function sources(root) {
  return readdirSync(root, { withFileTypes: true }).flatMap(entry => {
    const path = resolve(root, entry.name);
    if (entry.isDirectory()) return sources(path);
    return /\.(java|ts|vue)$/.test(entry.name) && !/\.(test|spec)\./.test(entry.name) ? [path] : [];
  });
}

/** Run all product roots; a missing project is a failed delivery check, not a skipped check. */
export function checkGovernance() {
  const findings = [];
  let checked = 0;
  for (const [project, root, emptySdkAllowed] of roots) {
    try {
      if (emptySdkAllowed && !existsSync(root)) continue;
      for (const path of sources(root)) {
        const key = project + '/' + relative(root, path).replaceAll('\\', '/');
        findings.push(...inspectSource(key, readFileSync(path, 'utf8')).findings);
        checked++;
      }
    } catch (failure) { findings.push(`${project}: cannot inspect source (${failure.code || 'unknown'})`); }
  }
  for (const module of ['fcc-common', 'fcc-server', 'fcc-admin', 'fcc-server-starter', 'fcc-admin-starter']) {
    try { findings.push(...inspectPom(module, readFileSync(resolve(repository, module, 'pom.xml'), 'utf8'))); }
    catch (failure) { findings.push(`${module}: cannot inspect POM (${failure.code || 'unknown'})`); }
  }
  return { checked, findings };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = checkGovernance();
  console.log(`Engineering governance: ${result.checked} production files, ${result.findings.length} findings`);
  result.findings.forEach(finding => console.error(finding));
  process.exitCode = result.findings.length ? 1 : 0;
}
