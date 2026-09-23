"use strict";

// 展示方向示例，不代表已确认的品牌代理、现货或技术规格。
const products = [
  {
    id: "mcu",
    title: "微控制器",
    en: "MICROCONTROLLERS",
    description: "从通用控制到智能应用的核心选择",
    tags: ["32 位 MCU", "低功耗", "嵌入式控制"],
    keywords: "mcu 单片机 微处理器 控制器",
    detail:
      "围绕处理能力、外设接口、存储资源与开发生态，梳理微控制器选型需求。适用于仪器仪表、工业控制、消费电子及智能终端等应用方向。",
    questions: [
      "目标应用与实时性要求",
      "主频、存储资源与外设数量",
      "供电范围、工作温度与封装",
      "现有软件平台与开发工具需求",
    ],
  },
  {
    id: "memory",
    title: "存储芯片",
    en: "MEMORY SOLUTIONS",
    description: "连接数据存储与高效读写需求",
    tags: ["NOR Flash", "NAND Flash", "DRAM"],
    keywords: "memory flash dram eeprom 存储 内存 闪存",
    detail:
      "根据代码存储、数据缓存或大容量数据保存等任务，评估存储类型、接口、容量与工作环境。具体型号需结合主控兼容性和应用要求确认。",
    questions: [
      "代码存储或数据存储用途",
      "容量、接口与读写性能",
      "工作电压与温度范围",
      "可靠性与生命周期要求",
    ],
  },
  {
    id: "power",
    title: "电源管理",
    en: "POWER MANAGEMENT",
    description: "为稳定运行与高效能量转换而选",
    tags: ["DC-DC", "LDO", "电池管理"],
    keywords: "power pmic dcdc ldo 电源 稳压 充电 电池",
    detail:
      "从输入电源到负载，围绕供电架构、转换效率、散热与保护需求，寻找适合的电源管理器件方向。",
    questions: [
      "输入电压范围与电源类型",
      "输出电压、电流与动态负载",
      "效率、纹波和热设计目标",
      "启动时序与保护要求",
    ],
  },
  {
    id: "analog",
    title: "模拟与接口",
    en: "ANALOG & INTERFACE",
    description: "让信号处理与设备连接更加清晰",
    tags: ["信号链", "运算放大器", "通信接口"],
    keywords: "analog adc dac can rs485 运放 接口 模拟 信号 隔离",
    detail:
      "从信号采集、放大、转换到通信连接，关注精度、噪声、带宽与系统兼容性，为应用搭建清晰的信号链路。",
    questions: [
      "信号类型、范围与精度",
      "采样速率、带宽与噪声目标",
      "接口协议与速率",
      "隔离、保护与抗干扰要求",
    ],
  },
  {
    id: "sensor",
    title: "传感器",
    en: "SENSING TECHNOLOGY",
    description: "感知物理世界，连接智能应用",
    tags: ["环境感知", "位置检测", "运动感知"],
    keywords: "sensor mems 传感 温度 湿度 压力 位置 运动",
    detail:
      "围绕温度、湿度、压力、位置或运动等检测需求，梳理测量范围、精度、响应速度与接口形式。",
    questions: [
      "需要测量的物理量与量程",
      "精度、重复性和响应时间",
      "工作环境与安装方式",
      "数字或模拟输出接口",
    ],
  },
  {
    id: "passive",
    title: "被动元件",
    en: "PASSIVE COMPONENTS",
    description: "从基础元件，完善每一处电路细节",
    tags: ["电阻", "电容", "电感"],
    keywords: "passive resistor capacitor 电阻 电容 电感 磁珠 被动",
    detail:
      "从基础的阻容感器件出发，确认额定参数、容差、温度特性、尺寸与封装，配合整机设计与采购需求。",
    questions: [
      "标称值、容差与额定电压",
      "功率、电流或损耗要求",
      "封装尺寸与装配方式",
      "温度特性与应用环境",
    ],
  },
];
const scenes = {
  industrial: {
    title: "工业控制",
    en: "INDUSTRIAL AUTOMATION",
    headline: "让工业更智能<br>让控制更精准",
    description:
      "面向自动化设备、仪器仪表与电机控制，关注器件的稳定性、实时响应及长期供货需求。",
    tags: ["工业 MCU", "隔离与接口", "电源管理"],
  },
  automotive: {
    title: "汽车电子",
    en: "AUTOMOTIVE ELECTRONICS",
    headline: "连接智慧出行<br>关注每一程体验",
    description:
      "围绕车载显示、车身控制与信息交互，梳理温度、可靠性及质量要求；具体车规资质需按型号确认。",
    tags: ["车身控制", "车载存储", "接口与保护"],
  },
  iot: {
    title: "智能物联",
    en: "INTELLIGENT CONNECTIVITY",
    headline: "从智能终端<br>走向万物互联",
    description:
      "面向智能家居、可穿戴设备及物联终端，平衡低功耗、感知能力、存储和连接需求。",
    tags: ["低功耗 MCU", "传感器", "存储芯片"],
  },
  energy: {
    title: "新能源",
    en: "ENERGY & SUSTAINABILITY",
    headline: "让能量流动<br>更高效、更从容",
    description:
      "从能源监测到充电与储能应用，围绕电源转换、信号采集及系统控制梳理元器件需求。",
    tags: ["电源管理", "信号采集", "控制与隔离"],
  },
};
const articles = [
  {
    title: "存储器选型，不止看容量",
    kicker: "MEMORY / 选型笔记",
    sections: [
      [
        "先明确存储任务",
        "代码启动、运行缓存与长期数据保存，对器件的需求不同。选型前先确认主控支持的存储类型和接口，再讨论容量与速度。",
      ],
      [
        "把使用边界写清楚",
        "记录供电电压、工作温度、空间尺寸、接口速率与读写方式。对于频繁写入的应用，还需要结合数据手册确认耐久性与管理方式。",
      ],
      [
        "替换前，需要验证兼容性",
        "封装相同并不等于可以直接替换。命令集、时序、引脚定义与软件驱动等都需要核对，并通过实际系统验证。",
      ],
    ],
  },
  {
    title: "一份清晰的 BOM，让沟通更高效",
    kicker: "PROCUREMENT / 采购参考",
    sections: [
      [
        "让型号信息完整",
        "尽量提供完整制造商型号，保留尾缀与封装信息。不同后缀可能对应不同温度等级、包装形式或规格，采购时需要逐一确认。",
      ],
      [
        "区分用量、交期与阶段",
        "明确样品数量、首批需求、后续预计用量及期望交期。同时说明项目处于研发验证、小批试产还是量产阶段。",
      ],
      [
        "提前标注特殊要求",
        "是否接受替代型号、是否有批次或包装要求、是否需要特定资料，这些信息都可以放入备注，减少沟通往返。",
      ],
    ],
  },
  {
    title: "从应用出发，梳理电源管理需求",
    kicker: "POWER / 选型笔记",
    sections: [
      [
        "从输入与负载开始",
        "确认输入电源的最低、典型和最高电压，以及负载的静态、峰值和动态电流。只看典型工况，可能遗漏边界需求。",
      ],
      [
        "把效率与热设计一起考虑",
        "器件选择需要结合转换拓扑、负载变化、PCB 布局、散热空间与环境温度。具体性能以数据手册及实际验证结果为准。",
      ],
      [
        "别忽略启动与保护",
        "记录上电顺序、软启动、过流、过温与短路保护等要求。如果存在多个电源轨，还需要考虑各电源之间的配合。",
      ],
    ],
  },
];

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const arrow =
  '<svg class="icon" aria-hidden="true"><use href="#diagonal"/></svg>';
let activeCategory = "all";
let activeQuery = "";
let activeScene = "industrial";
let lastOpener = null;
let inquiryText = "";

function renderProducts() {
  const matched = products.filter(
    (p) =>
      (activeCategory === "all" || p.id === activeCategory) &&
      `${p.title} ${p.en} ${p.description} ${p.tags.join(" ")} ${p.keywords}`
        .toLowerCase()
        .includes(activeQuery),
  );
  $("#product-grid").innerHTML = matched
    .map(
      (p) =>
        `<button class="product-card" data-product="${p.id}" aria-label="了解${p.title}"><div class="product-image"><img src="assets/product-${p.id}.svg" width="400" height="260" alt="" loading="lazy"><span class="product-code">${p.en}</span></div><div class="product-info"><h3>${p.title}${arrow}</h3><p>${p.description}</p><div class="product-specs">${p.tags.map((t) => `<span>${t}</span>`).join("")}</div></div></button>`,
    )
    .join("");
  $("#result-count").textContent =
    `${String(matched.length).padStart(2, "0")} / 产品方向${activeQuery ? " · 筛选结果" : ""}`;
  $("#product-grid").hidden = matched.length === 0;
  $("#empty-state").hidden = matched.length > 0;
  $$("[data-category]").forEach((b) => {
    b.classList.toggle("active", b.dataset.category === activeCategory);
    b.setAttribute(
      "aria-pressed",
      String(b.dataset.category === activeCategory),
    );
  });
}
$$("[data-category]").forEach((b) =>
  b.addEventListener("click", () => {
    activeCategory = b.dataset.category;
    renderProducts();
  }),
);
$("#finder-form").addEventListener("submit", (e) => {
  e.preventDefault();
  activeCategory = "all";
  activeQuery = $("#product-search").value.trim().toLowerCase();
  renderProducts();
  $("#products").scrollIntoView({ behavior: "auto" });
});
$("#product-search").addEventListener("input", (e) => {
  if (!e.target.value) {
    activeQuery = "";
    renderProducts();
  }
});
$("#reset-search").addEventListener("click", () => {
  activeCategory = "all";
  activeQuery = "";
  $("#product-search").value = "";
  renderProducts();
  $('[data-category="all"]').focus({ preventScroll: true });
});
$(".search-trigger").addEventListener("click", () => {
  $("#finder-form").scrollIntoView({ behavior: "auto", block: "center" });
  $("#product-search").focus({ preventScroll: true });
  closeMenu();
});

function openModal(dialog) {
  const existing = $("dialog[open]");
  if (!existing) lastOpener = document.activeElement;
  if (existing) existing.close();
  dialog.showModal();
  document.body.classList.add("modal-open");
  dialog.scrollTop = 0;
}
$$("dialog").forEach((dialog) => {
  $(".dialog-close", dialog).addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (e) => {
    if (e.target !== dialog) return;
    const r = dialog.getBoundingClientRect();
    if (
      e.clientX < r.left ||
      e.clientX > r.right ||
      e.clientY < r.top ||
      e.clientY > r.bottom
    )
      dialog.close();
  });
  dialog.addEventListener("close", () => {
    if (!$("dialog[open]")) {
      document.body.classList.remove("modal-open");
      if (lastOpener?.isConnected) lastOpener.focus({ preventScroll: true });
    }
  });
});
function openInquiry(category = "综合咨询") {
  $("#inquiry-form").hidden = false;
  $("#inquiry-result").hidden = true;
  $("#form-error").hidden = true;
  $("#inquiry-form").elements.category.value = category;
  if (!$("#inquiry-form").elements.category.value)
    $("#inquiry-form").elements.category.value = "综合咨询";
  openModal($("#inquiry-dialog"));
}
$$("[data-inquiry]").forEach((b) =>
  b.addEventListener("click", () => openInquiry()),
);
$("#product-grid").addEventListener("click", (e) => {
  const button = e.target.closest("[data-product]");
  if (!button) return;
  const p = products.find((item) => item.id === button.dataset.product);
  $("#detail-content").innerHTML =
    `<p class="eyebrow">PRODUCT PORTFOLIO / ${p.en}</p><h2 id="detail-title">${p.title}</h2><img class="detail-art" src="assets/product-${p.id}.svg" alt="${p.title}概念图"><p class="detail-lead">${p.detail}</p><div class="detail-tags">${p.tags.map((t) => `<span>${t}</span>`).join("")}</div><div class="detail-body"><h3>开始选型前，可以先确认</h3><ul>${p.questions.map((q) => `<li>${q}</li>`).join("")}</ul></div><p class="detail-note">页面展示产品方向示例，具体品牌、型号、参数与供货情况需按实际需求确认。</p><button class="button button-dark" id="product-inquiry">咨询${p.title}${arrow}</button>`;
  $("#product-inquiry").addEventListener("click", () => openInquiry(p.title));
  openModal($("#detail-dialog"));
});

function selectScene(key, focus = false) {
  activeScene = key;
  const scene = scenes[key];
  $$("[data-scene]").forEach((b) => {
    const selected = b.dataset.scene === key;
    b.setAttribute("aria-selected", String(selected));
    b.tabIndex = selected ? 0 : -1;
    if (selected && focus) b.focus();
  });
  $("#application-panel").setAttribute("aria-labelledby", `tab-${key}`);
  $("#scene-image").src = `assets/scene-${key}.svg`;
  $("#scene-image").alt = `${scene.title}场景概念插画`;
  $("#scene-kicker").textContent = scene.en;
  $("#scene-title").innerHTML = scene.headline;
  $("#scene-description").textContent = scene.description;
  $("#scene-tags").innerHTML = scene.tags
    .map((t) => `<span>${t}</span>`)
    .join("");
}
$$("[data-scene]").forEach((button, index) => {
  button.addEventListener("click", () => selectScene(button.dataset.scene));
  button.addEventListener("keydown", (e) => {
    const keys = Object.keys(scenes);
    let next;
    if (e.key === "ArrowRight") next = (index + 1) % keys.length;
    else if (e.key === "ArrowLeft")
      next = (index + keys.length - 1) % keys.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = keys.length - 1;
    else return;
    e.preventDefault();
    selectScene(keys[next], true);
  });
});
$("#scene-inquiry").addEventListener("click", () =>
  openInquiry(scenes[activeScene].title),
);
$$("[data-article]").forEach((button) =>
  button.addEventListener("click", () => {
    const article = articles[Number(button.dataset.article)];
    $("#detail-content").innerHTML =
      `<p class="eyebrow">${article.kicker}</p><h2 id="detail-title">${article.title}</h2><div class="detail-body">${article.sections.map(([heading, text]) => `<h3>${heading}</h3><p>${text}</p>`).join("")}</div><p class="detail-note">通用选型参考，具体设计请结合器件数据手册与实际应用验证。</p>`;
    openModal($("#detail-dialog"));
  }),
);
$("#privacy-button").addEventListener("click", () => {
  $("#detail-content").innerHTML =
    '<p class="eyebrow">PRIVACY / DESIGN PREVIEW</p><h2 id="detail-title">隐私说明</h2><div class="detail-body"><h3>静态预览中的信息处理</h3><p>本站为光联微官网设计初稿，无后端接口，不使用统计追踪、Cookie 或本地持久化存储。产品搜索只在当前页面执行。</p><h3>您填写的需求信息</h3><p>表单内容只保存在当前页面内存中，不会上传或发送。刷新或关闭页面后，填写内容将丢失。只有点击下载清单时，才会在您的设备上生成文本文件。</p><h3>正式上线前</h3><p>如后续接入询价接收或客户服务系统，将根据实际的信息处理方式更新本说明，并补充正式联系渠道。</p></div>';
  openModal($("#detail-dialog"));
});

$("#inquiry-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  const values = Object.fromEntries(
    [...new FormData(form)].map(([key, value]) => [key, String(value).trim()]),
  );
  let error = "";
  if (!values.name || values.message.length < 5)
    error = "请填写称呼，并用至少 5 个字符描述您的需求。";
  else if (!(
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.contact) ||
    /^[+\d][\d\s()（）-]{5,24}$/.test(values.contact)
  ))
    error = "请填写有效的联系电话或邮箱。";
  if (error) {
    $("#form-error").textContent = error;
    $("#form-error").hidden = false;
    return;
  }
  $("#form-error").hidden = true;
  inquiryText = `光联微电子有限公司 · 询价需求清单\n状态：本地草稿，尚未发送\n\n称呼：${values.name}\n公司：${values.company || "未填写"}\n联系方式：${values.contact}\n需求方向：${values.category}\n\n需求描述：\n${values.message}\n\n整理时间：${new Date().toLocaleString("zh-CN", { hour12: false })}\n`;
  $("#inquiry-preview").textContent = inquiryText;
  form.hidden = true;
  $("#inquiry-result").hidden = false;
  $("#inquiry-dialog").scrollTop = 0;
  $("#download-inquiry").focus({ preventScroll: true });
});
$("#edit-inquiry").addEventListener("click", () => {
  $("#inquiry-result").hidden = true;
  $("#inquiry-form").hidden = false;
  $("#inquiry-form").elements.message.focus();
});
$("#download-inquiry").addEventListener("click", () => {
  const url = URL.createObjectURL(
    new Blob(["\uFEFF", inquiryText], { type: "text/plain;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = `光联微-询价清单-${new Date().toISOString().slice(0, 10)}.txt`;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});

function closeMenu() {
  $(".main-nav").classList.remove("open");
  $(".menu-toggle").setAttribute("aria-expanded", "false");
  $(".menu-toggle").setAttribute("aria-label", "打开导航菜单");
}
$(".menu-toggle").addEventListener("click", () => {
  const open = $(".main-nav").classList.toggle("open");
  $(".menu-toggle").setAttribute("aria-expanded", String(open));
  $(".menu-toggle").setAttribute(
    "aria-label",
    open ? "关闭导航菜单" : "打开导航菜单",
  );
});
$$(".main-nav a").forEach((a) => a.addEventListener("click", closeMenu));
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && $(".main-nav").classList.contains("open")) {
    closeMenu();
    $(".menu-toggle").focus();
  }
});
document.addEventListener("click", (e) => {
  if (!e.target.closest(".site-header")) closeMenu();
});
window.matchMedia("(min-width:1051px)").addEventListener("change", (e) => {
  if (e.matches) closeMenu();
});
const navLinks = $$(".main-nav a");
function updateNavigation() {
  let current = "#home";
  for (const link of navLinks)
    if ($(link.hash).getBoundingClientRect().top < 165) current = link.hash;
  for (const link of navLinks) {
    const selected = link.hash === current;
    link.classList.toggle("active", selected);
    if (selected) link.setAttribute("aria-current", "location");
    else link.removeAttribute("aria-current");
  }
  $(".back-to-top").hidden = window.scrollY < 500;
}
let scheduled = false;
window.addEventListener(
  "scroll",
  () => {
    if (!scheduled) {
      scheduled = true;
      requestAnimationFrame(() => {
        updateNavigation();
        scheduled = false;
      });
    }
  },
  { passive: true },
);
$("#copyright-year").textContent = new Date().getFullYear();
renderProducts();
updateNavigation();
