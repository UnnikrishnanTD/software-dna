/** A parsed `owner/repository` pair. */
export interface RepositoryRefParts {
  readonly owner: string;
  readonly name: string;
}

/**
 * Accepts a repository reference in any of the forms people actually paste:
 *
 *   https://github.com/owner/repo
 *   https://github.com/owner/repo.git
 *   github.com/owner/repo
 *   git@github.com:owner/repo.git
 *   owner/repo
 *
 * This lives in one place deliberately. The pattern was previously copied
 * into both the input form and the analysis service, and the two drifted:
 * the form advertised SSH remotes while rejecting them, because neither
 * copy handled the `git@` prefix.
 */
const PATTERN =
  /^(?:(?:https?:\/\/)?(?:[\w.-]+@)?(?:www\.)?github\.com[/:])?([\w.-]+)\/([\w.-]+?)(?:\.git)?\/?$/i;

export function parseRepositoryRef(input: string): RepositoryRefParts | null {
  const match = PATTERN.exec(input.trim());
  return match ? { owner: match[1], name: match[2] } : null;
}

/** True when both references point at the same repository. */
export function isSameRepository(
  a: RepositoryRefParts,
  b: RepositoryRefParts,
): boolean {
  return (
    a.owner.toLowerCase() === b.owner.toLowerCase() &&
    a.name.toLowerCase() === b.name.toLowerCase()
  );
}
