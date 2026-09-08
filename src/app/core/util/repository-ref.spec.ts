import { isSameRepository, parseRepositoryRef } from './repository-ref';

describe('parseRepositoryRef', () => {
  const expected = { owner: 'nova-retail', name: 'nova-platform' };

  /**
   * The form advertises that "a full URL, an SSH remote or a bare
   * owner/repository all work". Each of these must actually parse, or the
   * input rejects something the interface promised to accept.
   */
  it('accepts every form the interface advertises', () => {
    const forms = [
      'https://github.com/nova-retail/nova-platform',
      'https://github.com/nova-retail/nova-platform.git',
      'https://www.github.com/nova-retail/nova-platform',
      'http://github.com/nova-retail/nova-platform',
      'github.com/nova-retail/nova-platform',
      'github.com/nova-retail/nova-platform/',
      'git@github.com:nova-retail/nova-platform.git',
      'git@github.com:nova-retail/nova-platform',
      'nova-retail/nova-platform',
      '  nova-retail/nova-platform  ',
    ];

    for (const form of forms) {
      expect(parseRepositoryRef(form)).withContext(form).toEqual(expected);
    }
  });

  it('keeps dots inside a repository name', () => {
    expect(parseRepositoryRef('acme/order.service')).toEqual({
      owner: 'acme',
      name: 'order.service',
    });
  });

  it('strips only a trailing .git, not a name that merely contains it', () => {
    expect(parseRepositoryRef('acme/gitignore')).toEqual({
      owner: 'acme',
      name: 'gitignore',
    });
  });

  it('preserves the case it was given', () => {
    expect(parseRepositoryRef('NOVA-Retail/Nova-Platform')).toEqual({
      owner: 'NOVA-Retail',
      name: 'Nova-Platform',
    });
  });

  it('rejects references that are not repositories', () => {
    const rejected = [
      '',
      '   ',
      'nova-platform',
      'https://github.com/nova-retail',
      'https://github.com/nova-retail/nova-platform/tree/main/src',
      'not a url at all',
      'https://example.com/a/b/c',
    ];

    for (const value of rejected) {
      expect(parseRepositoryRef(value)).withContext(value).toBeNull();
    }
  });
});

describe('isSameRepository', () => {
  it('matches regardless of case', () => {
    expect(
      isSameRepository(
        { owner: 'Nova-Retail', name: 'Nova-Platform' },
        { owner: 'nova-retail', name: 'nova-platform' },
      ),
    ).toBe(true);
  });

  it('does not match a different repository from the same owner', () => {
    expect(
      isSameRepository(
        { owner: 'nova-retail', name: 'nova-platform' },
        { owner: 'nova-retail', name: 'nova-docs' },
      ),
    ).toBe(false);
  });
});
