module.exports = {
  title: 'ZIO gRPC',
  tagline: 'Build gRPC clients and services with ZIO',
  url: 'https://scalapb.github.io/zio-grpc/',
  baseUrl: '/zio-grpc/',
  favicon: 'img/favicon.ico',
  organizationName: 'scalapb', // Usually your GitHub org/user name.
  projectName: 'zio-grpc', // Usually your repo name.
  themeConfig: {
    navbar: {
      title: 'ZIO gRPC',
      logo: {
        alt: 'My Site Logo',
        src: 'img/logo.svg',
      },
      links: [
        {
          to: 'docs/',
          activeBasePath: 'docs',
          label: 'Docs',
          position: 'left',
        },
        // {to: 'blog', label: 'Blog', position: 'left'},
        {
          href: 'https://github.com/scalapb/zio-grpc',
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
              label: 'Introduction',
              to: 'docs/',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'Stack Overflow',
              href: 'https://stackoverflow.com/questions/tagged/scalapb',
            },
            {
              label: 'Gitter',
              href: 'https://gitter.im/ScalaPB/community',
            },
          ],
        },
        {
          title: 'More',
          items: [
            /*
            {
              label: 'Blog',
              to: 'blog',
            },
            */
            {
              label: 'GitHub',
              href: 'https://github.com/scalapb/zio-grpc',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Nadav Samet`,
    },
    prism: {
      additionalLanguages: ['scala', 'protobuf'],
      theme: require('prism-react-renderer/themes/dracula')
    }
  },
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          // It is recommended to set document id as docs home page (`docs/` path).
          homePageId: 'intro',
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
          editUrl:
            'https://github.com/scalapb/zio-grpc/edit/master/foo/docs/',
          path: '../zio-grpc-docs/target/mdoc'
        },
        blog: {
          showReadingTime: true,
          // Please change this to your repo.
          editUrl:
            'https://github.com/scalapb/zio-grpc/edit/master/website/blog/',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],
  plugins: [
    '@docusaurus/plugin-content-docs',
  ]
};
