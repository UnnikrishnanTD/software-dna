import { Hotspot } from '../../models/hotspot.model';
import { complexityBandFor } from '../graph-builder';
import { RiskLevel } from '../../models/architecture.model';

/**
 * A hotspot is where high complexity and high change frequency meet. Both
 * axes come from the repository itself; the composite risk score below is
 * what ranks them.
 */
type HotspotSpec = Omit<
  Hotspot,
  'complexity' | 'riskScore' | 'severity' | 'coverage'
> & { readonly coverage: number };

function riskScoreFor(spec: HotspotSpec): number {
  // Change frequency and complexity are the two axes of the map; coverage
  // and bug history modulate how dangerous that intersection actually is.
  const churn = Math.min(spec.changes / 150, 1);
  const complexity = Math.min(spec.complexityScore / 34, 1);
  const coverageGap = (100 - spec.coverage) / 100;
  const defectPressure = Math.min(spec.bugFixes / 10, 1);
  const coupling = Math.min(spec.dependencies / 24, 1);

  const composite =
    churn * 0.28 +
    complexity * 0.26 +
    coverageGap * 0.2 +
    defectPressure * 0.14 +
    coupling * 0.12;

  return Math.round(composite * 100);
}

function severityFor(riskScore: number): RiskLevel {
  if (riskScore >= 72) return 'critical';
  if (riskScore >= 55) return 'high';
  if (riskScore >= 35) return 'medium';
  return 'low';
}

const SPECS: readonly HotspotSpec[] = [
  {
    id: 'hs-user-service',
    name: 'UserService.java',
    path: 'services/identity/src/main/java/com/atlas/identity/UserService.java',
    changes: 147,
    complexityScore: 34,
    dependencies: 23,
    bugFixes: 8,
    linesOfCode: 1284,
    coverage: 44,
    contributors: 11,
    architectureNodeId: 'user-service',
    rationale:
      'This file changes more often than any other in the repository while carrying the largest dependency surface and the lowest coverage of any domain service. Every one of those properties amplifies the others.',
    recommendation:
      'Extract addresses, consent and loyalty enrolment into their own services, then bring coverage up before further change.',
  },
  {
    id: 'hs-order-service',
    name: 'OrderService.java',
    path: 'services/order/src/main/java/com/atlas/order/OrderService.java',
    changes: 118,
    complexityScore: 29,
    dependencies: 19,
    bugFixes: 6,
    linesOfCode: 1042,
    coverage: 58,
    contributors: 9,
    architectureNodeId: 'order-service',
    rationale:
      'Order lifecycle logic has absorbed split shipments and partial refunds without a corresponding rise in test coverage. It calls into eight other domain services.',
    recommendation:
      'Raise coverage on the fulfilment and refund paths before extracting the split-shipment logic.',
  },
  {
    id: 'hs-promotion-service',
    name: 'PromotionService.java',
    path: 'services/pricing/src/main/java/com/atlas/pricing/PromotionService.java',
    changes: 94,
    complexityScore: 24,
    dependencies: 11,
    bugFixes: 7,
    linesOfCode: 836,
    coverage: 68,
    contributors: 6,
    architectureNodeId: 'promotion-service',
    rationale:
      'Discount stacking precedence is expressed as deeply nested conditionals. It has the second-highest defect count in the codebase despite moderate size.',
    recommendation:
      'Replace the nested precedence rules with an explicit rule table and property-based tests.',
  },
  {
    id: 'hs-analytics-panel',
    name: 'analytics-panel.component.ts',
    path: 'web/src/app/features/admin/analytics-panel.component.ts',
    changes: 86,
    complexityScore: 21,
    dependencies: 14,
    bugFixes: 5,
    linesOfCode: 706,
    coverage: 38,
    contributors: 5,
    architectureNodeId: 'analytics-panel',
    rationale:
      'Data shaping, formatting and rendering all live in one component, so every reporting change touches presentation code. It has the lowest coverage in the web application.',
    recommendation:
      'Move aggregation into a facade and cover it directly; leave the component rendering only.',
  },
  {
    id: 'hs-checkout-stepper',
    name: 'checkout-stepper.component.ts',
    path: 'web/src/app/features/checkout/checkout-stepper.component.ts',
    changes: 79,
    complexityScore: 19,
    dependencies: 12,
    bugFixes: 4,
    linesOfCode: 561,
    coverage: 63,
    contributors: 7,
    architectureNodeId: 'checkout-stepper',
    rationale:
      'Step validation rules are encoded directly in the component, so each new checkout requirement widens the same conditional.',
    recommendation:
      'Lift step validation into the checkout facade as declarative rules.',
  },
  {
    id: 'hs-pricing-service',
    name: 'PricingService.java',
    path: 'services/pricing/src/main/java/com/atlas/pricing/PricingService.java',
    changes: 71,
    complexityScore: 20,
    dependencies: 9,
    bugFixes: 3,
    linesOfCode: 712,
    coverage: 85,
    contributors: 6,
    architectureNodeId: 'pricing-service',
    rationale:
      'Changes often, but strong coverage and a flat structure keep the risk contained. Worth watching rather than acting on.',
    recommendation:
      'No action needed. Keep coverage above 80% as tax rules expand.',
  },
  {
    id: 'hs-inventory-service',
    name: 'InventoryService.java',
    path: 'services/inventory/src/main/java/com/atlas/inventory/InventoryService.java',
    changes: 63,
    complexityScore: 18,
    dependencies: 8,
    bugFixes: 4,
    linesOfCode: 648,
    coverage: 79,
    contributors: 5,
    architectureNodeId: 'inventory-service',
    rationale:
      'Reservation logic is concurrency-sensitive and has produced four defects, though coverage is reasonable.',
    recommendation:
      'Add concurrency tests around reservation expiry.',
  },
  {
    id: 'hs-admin-module',
    name: 'AdminModule routes',
    path: 'web/src/app/features/admin',
    changes: 58,
    complexityScore: 17,
    dependencies: 10,
    bugFixes: 2,
    linesOfCode: 894,
    coverage: 51,
    contributors: 4,
    architectureNodeId: 'admin-module',
    rationale:
      'Internal tooling has grown quickly with the least test attention of any feature module.',
    recommendation:
      'Cover the pricing and promotion admin flows, which mutate production data.',
  },
  {
    id: 'hs-shipping-service',
    name: 'ShippingService.java',
    path: 'services/fulfilment/src/main/java/com/atlas/fulfilment/ShippingService.java',
    changes: 47,
    complexityScore: 16,
    dependencies: 7,
    bugFixes: 2,
    linesOfCode: 524,
    coverage: 81,
    contributors: 4,
    architectureNodeId: 'shipping-service',
    rationale:
      'Carrier integrations change with external contracts rather than internal decisions.',
    recommendation: 'Keep carrier adapters behind the current interface.',
  },
  {
    id: 'hs-catalog-service',
    name: 'CatalogService.java',
    path: 'services/catalog/src/main/java/com/atlas/catalog/CatalogService.java',
    changes: 41,
    complexityScore: 15,
    dependencies: 8,
    bugFixes: 1,
    linesOfCode: 592,
    coverage: 87,
    contributors: 6,
    architectureNodeId: 'catalog-service',
    rationale:
      'Frequently touched but well covered and structurally flat — a healthy hotspot.',
    recommendation: 'No action needed.',
  },
  {
    id: 'hs-cart-store',
    name: 'cart.store.ts',
    path: 'web/src/app/core/state/cart.store.ts',
    changes: 38,
    complexityScore: 15,
    dependencies: 6,
    bugFixes: 3,
    linesOfCode: 418,
    coverage: 88,
    contributors: 5,
    architectureNodeId: 'cart-store',
    rationale:
      'Optimistic rollback paths account for all three defects, but coverage is strong.',
    recommendation: 'Add regression tests for concurrent quantity updates.',
  },
  {
    id: 'hs-search-service',
    name: 'SearchService.java',
    path: 'services/catalog/src/main/java/com/atlas/catalog/SearchService.java',
    changes: 34,
    complexityScore: 13,
    dependencies: 6,
    bugFixes: 2,
    linesOfCode: 436,
    coverage: 76,
    contributors: 4,
    architectureNodeId: 'search-service',
    rationale: 'Relevance tuning drives most of the churn; structure is stable.',
    recommendation: 'No action needed.',
  },
  {
    id: 'hs-payment-service',
    name: 'PaymentService.java',
    path: 'services/checkout/src/main/java/com/atlas/checkout/PaymentService.java',
    changes: 29,
    complexityScore: 22,
    dependencies: 9,
    bugFixes: 1,
    linesOfCode: 764,
    coverage: 83,
    contributors: 4,
    architectureNodeId: 'payment-service',
    rationale:
      'Complex but deliberately stable — payment logic changes rarely and is well covered.',
    recommendation: 'No action needed. Preserve the current coverage level.',
  },
  {
    id: 'hs-api-client',
    name: 'api-client.ts',
    path: 'web/src/app/core/http/api-client.ts',
    changes: 26,
    complexityScore: 10,
    dependencies: 4,
    bugFixes: 1,
    linesOfCode: 264,
    coverage: 93,
    contributors: 8,
    architectureNodeId: 'api-client',
    rationale: 'Widely depended upon but small, simple and heavily covered.',
    recommendation: 'No action needed.',
  },
  {
    id: 'hs-product-detail',
    name: 'product-detail.component.ts',
    path: 'web/src/app/features/storefront/product-detail.component.ts',
    changes: 22,
    complexityScore: 11,
    dependencies: 7,
    bugFixes: 1,
    linesOfCode: 388,
    coverage: 80,
    contributors: 5,
    architectureNodeId: 'product-detail',
    rationale: 'Ordinary feature churn with healthy coverage.',
    recommendation: 'No action needed.',
  },
  {
    id: 'hs-session-store',
    name: 'session.store.ts',
    path: 'web/src/app/core/state/session.store.ts',
    changes: 18,
    complexityScore: 12,
    dependencies: 5,
    bugFixes: 2,
    linesOfCode: 296,
    coverage: 90,
    contributors: 4,
    architectureNodeId: 'session-store',
    rationale: 'Security-sensitive but stable and well covered.',
    recommendation: 'No action needed.',
  },
  {
    id: 'hs-search-bar',
    name: 'search-bar.component.ts',
    path: 'web/src/app/shared/search-bar.component.ts',
    changes: 12,
    complexityScore: 5,
    dependencies: 3,
    bugFixes: 0,
    linesOfCode: 158,
    coverage: 91,
    contributors: 3,
    architectureNodeId: 'search-bar',
    rationale: 'Small, stable and well covered.',
    recommendation: 'No action needed.',
  },
  {
    id: 'hs-order-repository',
    name: 'OrderRepository.java',
    path: 'services/order/src/main/java/com/atlas/order/OrderRepository.java',
    changes: 31,
    complexityScore: 7,
    dependencies: 3,
    bugFixes: 2,
    linesOfCode: 268,
    coverage: 91,
    contributors: 5,
    architectureNodeId: 'order-repository',
    rationale:
      'Low complexity, but the source of the N+1 query pattern flagged under Performance.',
    recommendation: 'Add a fetch join for split-shipment line items.',
  },
];

export const ATLAS_HOTSPOTS: readonly Hotspot[] = SPECS.map((spec) => {
  const riskScore = riskScoreFor(spec);
  return {
    ...spec,
    complexity: complexityBandFor(spec.complexityScore),
    riskScore,
    severity: severityFor(riskScore),
  };
}).sort((a, b) => b.riskScore - a.riskScore);
