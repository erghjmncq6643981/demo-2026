package com.chandler.fcc.server.customer.domain;

/** 统一号码输入规则，禁止将用户输入作为 FreeSWITCH 命令或拨号串。 */
public final class PhoneNumber {
    /** 工具类不可实例化。 */
    private PhoneNumber() {}

    /** 去除展示分隔符并验证号码，不擅自推断国家区号。
     * @param value 用户输入号码
     * @return 保留可选加号的纯数字号码
     * @throws IllegalArgumentException 号码为空或包含非法字符
     */
    public static String normalize(String value) {
        String number = value == null ? "" : value.trim().replaceAll("[ ()-]", "");
        if (!number.matches("\\+?[0-9]{2,20}")) throw new IllegalArgumentException("号码须为 2 至 20 位数字，可带国际区号加号");
        return number;
    }
}
