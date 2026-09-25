import type { AnswerEndpointType } from '../../../types/telephony';

/** 坐席可选择的已验证接听方式。 */
export interface EndpointOption {
  key: string;
  type: AnswerEndpointType;
  value: string;
  label: string;
  detail: string;
  disabled?: boolean;
}

interface EndpointFacts {
  workNo: string;
  webrtcWorkNo: string;
  sipExtensions: string[];
  mobilePhone: string;
}

/** 只从后端返回的绑定事实构造选项，不臆造分机。 */
export function buildEndpointOptions(facts: EndpointFacts): EndpointOption[] {
  const webrtc = facts.webrtcWorkNo || facts.workNo;
  const options: EndpointOption[] = webrtc
    ? [{
        key: endpointKey('WEBRTC', webrtc),
        type: 'WEBRTC',
        value: webrtc,
        label: 'WebRTC软电话',
        detail: `工号分机 ${webrtc}`,
      }]
    : [];

  [...new Set(facts.sipExtensions.filter(Boolean))].forEach((extension) => {
    options.push({
      key: endpointKey('SIP', extension),
      type: 'SIP',
      value: extension,
      label: 'SIP话机',
      detail: `分机 ${extension}`,
    });
  });

  if (facts.mobilePhone) {
    options.push({
      key: endpointKey('MOBILE', facts.mobilePhone),
      type: 'MOBILE',
      value: facts.mobilePhone,
      label: '手机',
      detail: '能力尚未开放',
      disabled: true,
    });
  }
  return options;
}

/** 构造不会把 Long ID 数值化的稳定选择键。 */
export function endpointKey(type: AnswerEndpointType, value: string): string {
  return `${type}:${value}`;
}

/** 将选择键还原成接口参数。 */
export function parseEndpointKey(key: string): Pick<EndpointOption, 'type' | 'value'> | null {
  const separator = key.indexOf(':');
  if (separator < 1) return null;
  const type = key.slice(0, separator);
  if (type !== 'WEBRTC' && type !== 'SIP' && type !== 'MOBILE') return null;
  const value = key.slice(separator + 1);
  return value ? { type, value } : null;
}
