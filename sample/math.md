# 公式渲染验证

覆盖本书出现的全部 LaTeX 结构。

## 1 分式与上下标

$$
S(x,y)=A\exp\!\Big(-\frac{(x-x_0)^2+(y-y_0)^2}{2\sigma^2}\Big)
$$

$$
\mathrm{SNR} = \frac{\mu_t - \mu_b}{\sigma_n}
$$

$$
\mathrm{SCR} = \frac{I_t - \mu_c}{\sigma_c}
$$

## 2 带上下限的大运算符

$$
\min_{B,S}\ \|B\|_*+\lambda \|S\|_1 \quad \text{s.t.}\ \ M=B+S
$$

## 3 最复杂的一条：嵌套 max/min/分式

$$
\mathrm{LCM} = \max_{n \in N_8} \Big(\min_{m \in N_8,\, m \ne n} \frac{C_m}{C_n}\Big) \cdot C_n - \min_{m \in N_8} C_m
$$

## 4 箭头与多行公式

$$
M \xrightarrow{\text{低秩稀疏}} B + S + N
$$

$$
\min_{B,S,N} \|B\|_*+\lambda_1 \|S\|_1 + \lambda_2 \|\nabla_h N\|_1 + \lambda_3 \|\nabla_v N\|_1
$$

## 5 行内公式

能量直径 d ≈ $2.35\sigma$ ，其中 $\sigma$ 控制扩散，$\mu_t$ 为目标均值。

## 6 根号与重音

$$
d = 2\sqrt{2\ln 2}\,\sigma \qquad \hat{x} = \bar{x} + \tilde{n}
$$

## 7 希腊字母与关系符

$$
\alpha \le \beta \ne \gamma \approx \delta \in \Omega \subseteq \mathbb{R}
$$
