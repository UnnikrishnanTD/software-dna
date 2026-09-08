import { ComplexityBand, RiskLevel } from '../../models/architecture.model';
import { FileMetrics, FileNode } from '../../models/codebase.model';
import { complexityBandFor } from '../graph-builder';
import { clamp } from '../../util/health';

/** The authored half of a file's metrics; bands and risk are derived. */
interface FileSpec {
  readonly complexityScore: number;
  readonly linesOfCode: number;
  readonly dependencies: number;
  readonly dependents: number;
  readonly changes: number;
  readonly coverage: number;
  readonly lastChanged: string;
  readonly primaryAuthor: string;
}

function riskFor(spec: FileSpec): RiskLevel {
  const score =
    Math.min(spec.complexityScore / 34, 1) * 0.35 +
    Math.min(spec.changes / 150, 1) * 0.25 +
    ((100 - spec.coverage) / 100) * 0.4;
  if (score >= 0.62) return 'critical';
  if (score >= 0.45) return 'high';
  if (score >= 0.27) return 'medium';
  return 'low';
}

/** A file's health, on the same 0–100 scale as every other score. */
function healthFor(spec: FileSpec, complexity: ComplexityBand): number {
  const complexityPenalty =
    complexity === 'very-high' ? 26 : complexity === 'high' ? 15 : complexity === 'medium' ? 7 : 0;
  const coveragePenalty = (100 - spec.coverage) * 0.42;
  const churnPenalty = Math.min(spec.changes / 150, 1) * 14;
  return Math.round(clamp(100 - complexityPenalty - coveragePenalty - churnPenalty, 0, 100));
}

let sequence = 0;

function file(
  name: string,
  path: string,
  language: string,
  spec: FileSpec,
  architectureNodeId?: string,
): FileNode {
  const complexity = complexityBandFor(spec.complexityScore);
  const metrics: FileMetrics = {
    linesOfCode: spec.linesOfCode,
    complexity,
    complexityScore: spec.complexityScore,
    dependencies: spec.dependencies,
    dependents: spec.dependents,
    changes: spec.changes,
    coverage: spec.coverage,
    risk: riskFor(spec),
    lastChanged: spec.lastChanged,
    primaryAuthor: spec.primaryAuthor,
  };
  return {
    id: `f${++sequence}`,
    name,
    path,
    type: 'file',
    language,
    metrics,
    health: healthFor(spec, complexity),
    architectureNodeId,
  };
}

/** Directory health is the size-weighted mean of its descendants. */
function dir(name: string, path: string, children: readonly FileNode[]): FileNode {
  const leaves = collectLeaves(children);
  const totalLines = leaves.reduce((sum, leaf) => sum + (leaf.metrics?.linesOfCode ?? 0), 0);
  const health =
    totalLines === 0
      ? 100
      : Math.round(
          leaves.reduce(
            (sum, leaf) => sum + (leaf.health ?? 100) * (leaf.metrics?.linesOfCode ?? 0),
            0,
          ) / totalLines,
        );
  return { id: `d${++sequence}`, name, path, type: 'directory', children, health };
}

function collectLeaves(nodes: readonly FileNode[]): FileNode[] {
  return nodes.flatMap((node) =>
    node.type === 'file' ? [node] : collectLeaves(node.children ?? []),
  );
}

const TS = 'TypeScript';
const JAVA = 'Java';

export const ATLAS_CODEBASE: FileNode = dir('atlas-commerce-platform', '', [
  dir('web', 'web', [
    dir('src', 'web/src', [
      dir('app', 'web/src/app', [
        dir('core', 'web/src/app/core', [
          dir('http', 'web/src/app/core/http', [
            file('api-client.ts', 'web/src/app/core/http/api-client.ts', TS, { complexityScore: 10, linesOfCode: 264, dependencies: 4, dependents: 12, changes: 26, coverage: 93, lastChanged: '2026-08-14', primaryAuthor: 'p.moreau' }, 'api-client'),
            file('auth.interceptor.ts', 'web/src/app/core/http/auth.interceptor.ts', TS, { complexityScore: 7, linesOfCode: 142, dependencies: 3, dependents: 1, changes: 14, coverage: 95, lastChanged: '2026-06-02', primaryAuthor: 'p.moreau' }, 'auth-interceptor'),
            file('error-mapper.ts', 'web/src/app/core/http/error-mapper.ts', TS, { complexityScore: 6, linesOfCode: 118, dependencies: 2, dependents: 3, changes: 9, coverage: 96, lastChanged: '2026-05-21', primaryAuthor: 'l.andersen' }),
          ]),
          dir('state', 'web/src/app/core/state', [
            file('cart.store.ts', 'web/src/app/core/state/cart.store.ts', TS, { complexityScore: 15, linesOfCode: 418, dependencies: 6, dependents: 5, changes: 38, coverage: 88, lastChanged: '2026-08-29', primaryAuthor: 'r.okafor' }, 'cart-store'),
            file('session.store.ts', 'web/src/app/core/state/session.store.ts', TS, { complexityScore: 12, linesOfCode: 296, dependencies: 5, dependents: 6, changes: 18, coverage: 90, lastChanged: '2026-07-08', primaryAuthor: 'p.moreau' }, 'session-store'),
            file('catalog.store.ts', 'web/src/app/core/state/catalog.store.ts', TS, { complexityScore: 11, linesOfCode: 274, dependencies: 4, dependents: 4, changes: 21, coverage: 85, lastChanged: '2026-08-03', primaryAuthor: 'r.okafor' }, 'catalog-store'),
          ]),
          dir('facades', 'web/src/app/core/facades', [
            file('checkout.facade.ts', 'web/src/app/core/facades/checkout.facade.ts', TS, { complexityScore: 18, linesOfCode: 466, dependencies: 9, dependents: 4, changes: 52, coverage: 69, lastChanged: '2026-09-01', primaryAuthor: 'r.okafor' }, 'checkout-facade'),
            file('product.facade.ts', 'web/src/app/core/facades/product.facade.ts', TS, { complexityScore: 9, linesOfCode: 238, dependencies: 5, dependents: 3, changes: 19, coverage: 87, lastChanged: '2026-07-19', primaryAuthor: 'm.tanaka' }, 'product-facade'),
          ]),
        ]),
        dir('features', 'web/src/app/features', [
          dir('storefront', 'web/src/app/features/storefront', [
            file('product-grid.component.ts', 'web/src/app/features/storefront/product-grid.component.ts', TS, { complexityScore: 7, linesOfCode: 246, dependencies: 5, dependents: 1, changes: 17, coverage: 86, lastChanged: '2026-08-11', primaryAuthor: 'm.tanaka' }, 'product-grid'),
            file('product-detail.component.ts', 'web/src/app/features/storefront/product-detail.component.ts', TS, { complexityScore: 11, linesOfCode: 388, dependencies: 7, dependents: 1, changes: 22, coverage: 80, lastChanged: '2026-08-25', primaryAuthor: 'm.tanaka' }, 'product-detail'),
            file('storefront.routes.ts', 'web/src/app/features/storefront/storefront.routes.ts', TS, { complexityScore: 3, linesOfCode: 64, dependencies: 3, dependents: 1, changes: 8, coverage: 100, lastChanged: '2026-04-17', primaryAuthor: 'm.tanaka' }),
          ]),
          dir('checkout', 'web/src/app/features/checkout', [
            file('checkout-stepper.component.ts', 'web/src/app/features/checkout/checkout-stepper.component.ts', TS, { complexityScore: 19, linesOfCode: 561, dependencies: 12, dependents: 1, changes: 79, coverage: 63, lastChanged: '2026-09-02', primaryAuthor: 'r.okafor' }, 'checkout-stepper'),
            file('payment-form.component.ts', 'web/src/app/features/checkout/payment-form.component.ts', TS, { complexityScore: 13, linesOfCode: 344, dependencies: 6, dependents: 1, changes: 31, coverage: 71, lastChanged: '2026-08-18', primaryAuthor: 'l.andersen' }, 'payment-form'),
            file('cart-drawer.component.ts', 'web/src/app/features/checkout/cart-drawer.component.ts', TS, { complexityScore: 10, linesOfCode: 302, dependencies: 4, dependents: 1, changes: 24, coverage: 77, lastChanged: '2026-08-07', primaryAuthor: 'r.okafor' }, 'cart-drawer'),
          ]),
          dir('account', 'web/src/app/features/account', [
            file('order-history.component.ts', 'web/src/app/features/account/order-history.component.ts', TS, { complexityScore: 6, linesOfCode: 214, dependencies: 4, dependents: 1, changes: 13, coverage: 84, lastChanged: '2026-06-28', primaryAuthor: 'j.abara' }, 'order-history'),
            file('profile.component.ts', 'web/src/app/features/account/profile.component.ts', TS, { complexityScore: 8, linesOfCode: 268, dependencies: 5, dependents: 1, changes: 16, coverage: 76, lastChanged: '2026-07-24', primaryAuthor: 'j.abara' }),
          ]),
          dir('admin', 'web/src/app/features/admin', [
            file('analytics-panel.component.ts', 'web/src/app/features/admin/analytics-panel.component.ts', TS, { complexityScore: 21, linesOfCode: 706, dependencies: 14, dependents: 1, changes: 86, coverage: 38, lastChanged: '2026-09-04', primaryAuthor: 'd.kowalski' }, 'analytics-panel'),
            file('inventory-table.component.ts', 'web/src/app/features/admin/inventory-table.component.ts', TS, { complexityScore: 12, linesOfCode: 384, dependencies: 7, dependents: 1, changes: 34, coverage: 57, lastChanged: '2026-08-21', primaryAuthor: 'd.kowalski' }),
          ]),
        ]),
        dir('shared', 'web/src/app/shared', [
          file('search-bar.component.ts', 'web/src/app/shared/search-bar.component.ts', TS, { complexityScore: 5, linesOfCode: 158, dependencies: 3, dependents: 4, changes: 12, coverage: 91, lastChanged: '2026-05-09', primaryAuthor: 'm.tanaka' }, 'search-bar'),
          file('money.pipe.ts', 'web/src/app/shared/money.pipe.ts', TS, { complexityScore: 4, linesOfCode: 86, dependencies: 1, dependents: 14, changes: 7, coverage: 98, lastChanged: '2026-03-30', primaryAuthor: 'l.andersen' }),
        ]),
        file('app-shell.component.ts', 'web/src/app/app-shell.component.ts', TS, { complexityScore: 6, linesOfCode: 184, dependencies: 5, dependents: 0, changes: 15, coverage: 88, lastChanged: '2026-07-02', primaryAuthor: 'p.moreau' }, 'app-shell'),
      ]),
    ]),
  ]),
  dir('services', 'services', [
    dir('identity', 'services/identity', [
      file('UserService.java', 'services/identity/src/main/java/com/atlas/identity/UserService.java', JAVA, { complexityScore: 34, linesOfCode: 1284, dependencies: 23, dependents: 3, changes: 147, coverage: 44, lastChanged: '2026-09-05', primaryAuthor: 'j.abara' }, 'user-service'),
      file('UserController.java', 'services/identity/src/main/java/com/atlas/identity/UserController.java', JAVA, { complexityScore: 11, linesOfCode: 288, dependencies: 5, dependents: 1, changes: 42, coverage: 72, lastChanged: '2026-08-30', primaryAuthor: 'j.abara' }, 'user-controller'),
      file('UserRepository.java', 'services/identity/src/main/java/com/atlas/identity/UserRepository.java', JAVA, { complexityScore: 6, linesOfCode: 212, dependencies: 2, dependents: 1, changes: 23, coverage: 93, lastChanged: '2026-07-15', primaryAuthor: 'j.abara' }, 'user-repository'),
    ]),
    dir('order', 'services/order', [
      file('OrderService.java', 'services/order/src/main/java/com/atlas/order/OrderService.java', JAVA, { complexityScore: 29, linesOfCode: 1042, dependencies: 19, dependents: 3, changes: 118, coverage: 58, lastChanged: '2026-09-03', primaryAuthor: 'r.okafor' }, 'order-service'),
      file('OrderController.java', 'services/order/src/main/java/com/atlas/order/OrderController.java', JAVA, { complexityScore: 12, linesOfCode: 318, dependencies: 4, dependents: 1, changes: 39, coverage: 81, lastChanged: '2026-08-26', primaryAuthor: 'r.okafor' }, 'order-controller'),
      file('OrderRepository.java', 'services/order/src/main/java/com/atlas/order/OrderRepository.java', JAVA, { complexityScore: 7, linesOfCode: 268, dependencies: 3, dependents: 3, changes: 31, coverage: 91, lastChanged: '2026-08-12', primaryAuthor: 'l.andersen' }, 'order-repository'),
    ]),
    dir('catalog', 'services/catalog', [
      file('CatalogService.java', 'services/catalog/src/main/java/com/atlas/catalog/CatalogService.java', JAVA, { complexityScore: 15, linesOfCode: 592, dependencies: 8, dependents: 3, changes: 41, coverage: 87, lastChanged: '2026-08-20', primaryAuthor: 'm.tanaka' }, 'catalog-service'),
      file('SearchService.java', 'services/catalog/src/main/java/com/atlas/catalog/SearchService.java', JAVA, { complexityScore: 13, linesOfCode: 436, dependencies: 6, dependents: 1, changes: 34, coverage: 76, lastChanged: '2026-08-16', primaryAuthor: 'm.tanaka' }, 'search-service'),
      file('ReviewService.java', 'services/catalog/src/main/java/com/atlas/catalog/ReviewService.java', JAVA, { complexityScore: 10, linesOfCode: 344, dependencies: 5, dependents: 1, changes: 19, coverage: 84, lastChanged: '2026-06-11', primaryAuthor: 'j.abara' }, 'review-service'),
      file('ProductRepository.java', 'services/catalog/src/main/java/com/atlas/catalog/ProductRepository.java', JAVA, { complexityScore: 8, linesOfCode: 284, dependencies: 2, dependents: 3, changes: 18, coverage: 90, lastChanged: '2026-05-27', primaryAuthor: 'm.tanaka' }, 'product-repository'),
    ]),
    dir('pricing', 'services/pricing', [
      file('PricingService.java', 'services/pricing/src/main/java/com/atlas/pricing/PricingService.java', JAVA, { complexityScore: 20, linesOfCode: 712, dependencies: 9, dependents: 4, changes: 71, coverage: 85, lastChanged: '2026-08-31', primaryAuthor: 'd.kowalski' }, 'pricing-service'),
      file('PromotionService.java', 'services/pricing/src/main/java/com/atlas/pricing/PromotionService.java', JAVA, { complexityScore: 24, linesOfCode: 836, dependencies: 11, dependents: 3, changes: 94, coverage: 68, lastChanged: '2026-09-01', primaryAuthor: 'd.kowalski' }, 'promotion-service'),
    ]),
    dir('checkout', 'services/checkout', [
      file('PaymentService.java', 'services/checkout/src/main/java/com/atlas/checkout/PaymentService.java', JAVA, { complexityScore: 22, linesOfCode: 764, dependencies: 9, dependents: 2, changes: 29, coverage: 83, lastChanged: '2026-07-30', primaryAuthor: 'l.andersen' }, 'payment-service'),
      file('CheckoutController.java', 'services/checkout/src/main/java/com/atlas/checkout/CheckoutController.java', JAVA, { complexityScore: 14, linesOfCode: 342, dependencies: 5, dependents: 1, changes: 36, coverage: 76, lastChanged: '2026-08-24', primaryAuthor: 'l.andersen' }, 'checkout-controller'),
    ]),
    dir('inventory', 'services/inventory', [
      file('InventoryService.java', 'services/inventory/src/main/java/com/atlas/inventory/InventoryService.java', JAVA, { complexityScore: 18, linesOfCode: 648, dependencies: 8, dependents: 2, changes: 63, coverage: 79, lastChanged: '2026-08-28', primaryAuthor: 'd.kowalski' }, 'inventory-service'),
    ]),
    dir('fulfilment', 'services/fulfilment', [
      file('ShippingService.java', 'services/fulfilment/src/main/java/com/atlas/fulfilment/ShippingService.java', JAVA, { complexityScore: 16, linesOfCode: 524, dependencies: 7, dependents: 2, changes: 47, coverage: 81, lastChanged: '2026-08-09', primaryAuthor: 'l.andersen' }, 'shipping-service'),
    ]),
    dir('notification', 'services/notification', [
      file('NotificationService.java', 'services/notification/src/main/java/com/atlas/notification/NotificationService.java', JAVA, { complexityScore: 9, linesOfCode: 318, dependencies: 4, dependents: 2, changes: 21, coverage: 88, lastChanged: '2026-06-19', primaryAuthor: 'p.moreau' }, 'notification-service'),
    ]),
  ]),
  dir('platform', 'platform', [
    file('CacheAdapter.java', 'platform/cache/src/main/java/com/atlas/platform/CacheAdapter.java', JAVA, { complexityScore: 9, linesOfCode: 246, dependencies: 3, dependents: 6, changes: 17, coverage: 86, lastChanged: '2026-07-11', primaryAuthor: 'p.moreau' }, 'cache-adapter'),
  ]),
  dir('infra', 'infra', [
    file('docker-compose.yml', 'infra/docker-compose.yml', 'YAML', { complexityScore: 3, linesOfCode: 148, dependencies: 4, dependents: 0, changes: 28, coverage: 100, lastChanged: '2026-08-05', primaryAuthor: 'p.moreau' }),
    file('schema.sql', 'infra/postgres/schema.sql', 'SQL', { complexityScore: 4, linesOfCode: 1860, dependencies: 0, dependents: 3, changes: 44, coverage: 100, lastChanged: '2026-08-27', primaryAuthor: 'l.andersen' }, 'postgres'),
  ]),
]);
