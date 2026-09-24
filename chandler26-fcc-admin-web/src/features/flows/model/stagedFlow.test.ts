import { describe, expect, it } from "vitest";
import {
  inboundStages,
  newInboundFlow,
  readStagedFlow,
  validateStagedFlow,
} from "./stagedFlow";

describe("stagedFlow", () => {
  it("creates the same complete inbound stage catalog used by the runtime", () => {
    expect(newInboundFlow().stages).toEqual(inboundStages);
    expect(newInboundFlow().stages).toHaveLength(12);
  });

  it("requires explicit if branches and an else route when the menu is enabled", () => {
    const flow = newInboundFlow();
    flow.menu = { enabled: true, prompt: "您好，请按 1 转人工", timeoutSeconds: 10 };
    flow.defaultRoute = { targetType: "GROUP", target: "service", queueSeconds: 120 };

    expect(validateStagedFlow(flow).map((issue) => issue.field)).toContain("branches");
    flow.branches = [
      { digit: "1", targetType: "GROUP", target: "service", queueSeconds: 120 },
    ];
    expect(validateStagedFlow(flow)).toEqual([]);
  });

  it("detects duplicate keys and invalid routing targets", () => {
    const flow = newInboundFlow();
    flow.menu = { enabled: true, prompt: "请选择", timeoutSeconds: 10 };
    flow.defaultRoute = { targetType: "AGENT", target: "901001", queueSeconds: 60 };
    flow.branches = [
      { digit: "1", targetType: "GROUP", target: "", queueSeconds: 120 },
      { digit: "1", targetType: "AGENT", target: "901002", queueSeconds: 120 },
    ];

    const issues = validateStagedFlow(flow);
    const messages = issues.map((issue) => issue.message);
    expect(messages.some((message) => message.includes("重复"))).toBe(true);
    expect(issues.some((issue) => issue.field === "target")).toBe(true);
  });

  it("rejects system notification models as editable business flows", () => {
    expect(() =>
      readStagedFlow(
        JSON.stringify({
          routeMode: "AUTO_DIAL",
          template: "NOTIFICATION",
          stages: ["ENTRY", "DIAL_CUSTOMER", "NOTIFY", "CONFIRM", "END"],
        }),
      ),
    ).toThrow("流程类型或运行模式不受支持");
  });
});
