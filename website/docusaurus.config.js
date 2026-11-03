// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Orbiter',
  tagline: 'An Android app for your self-hosted BookOrbit library',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://pranavajk1.github.io',
  baseUrl: '/bookorbit-android/',

  organizationName: 'pranavajk1',
  projectName: 'bookorbit-android',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/pranavajk1/bookorbit-android/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/docusaurus-social-card.jpg',
      colorMode: {
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'Orbiter',
        logo: {
          alt: 'Orbiter logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Docs',
          },
          {
            to: 'pathname:///privacy.html',
            label: 'Privacy Policy',
            position: 'right',
          },
          {
            href: 'https://github.com/pranavajk1/bookorbit-android',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Getting Started',
                to: '/docs/intro',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Privacy Policy',
                to: 'pathname:///privacy.html',
              },
              {
                label: 'GitHub',
                href: 'https://github.com/pranavajk1/bookorbit-android',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Orbiter contributors. Orbiter is unofficial and not endorsed by the BookOrbit project.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
      },
    }),
};

export default config;
