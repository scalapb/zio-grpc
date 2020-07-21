import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';

const features = [
  {
    imageUrl: 'img/undraw_docusaurus_mountain.svg',
    title: <>Start Quickly and Scale</>,
    description: (
      <>
        Build your first gRPC server in minutes and
        scale to production loads.
      </>
    ),
  },
  {
    imageUrl: 'img/undraw_docusaurus_react.svg',
    title: <>Functional and Type-safe</>,
    description: (
      <>
        Use the power of Functional Programming and the Scala compiler
        to build robust, correct and fully-featured gRPC servers.
      </>
    ),
  },
  {
    title: <>Stream with ZStream</>,
    imageUrl: 'img/waterfall.svg',
    description: (
      <>
        Use ZIO's feature rich ZStreams to create server streaming, client streaming
        and bi-directionally streaming RPC endpoints.
      </>
    ),
  },
  {
    title: <>Highly Concurrent</>,
    imageUrl: 'img/undraw_docusaurus_react.svg',
    description: (
      <>
        Leverage the power of ZIO to build asynchronous clients and servers
        without deadlocks and race conditions.
      </>
    ),
  },
  {
    title: <>Safe Cancellations</>,
    imageUrl: 'img/undraw_docusaurus_react.svg',
    description: (
      <>
        Safely cancel an RPC call by interrupting the effect. Resources on the server will never leak!
      </>
    ),
  },
  {
    title: <>Browser Ready</>,
    imageUrl: 'img/undraw_docusaurus_tree.svg',
    description: (
      <>
        Use ZIO gRPC with Scala.js to connect to your service from
        the browser with grpc-web.
      </>
    ),
  },
];

function Feature({imageUrl, title, description}) {
  const imgUrl = useBaseUrl(imageUrl);
  return (
    <div className={clsx('col col--4', styles.feature)}>
      {imgUrl && (
        <div className="text--center">
          <img className={styles.featureImage} src={imgUrl} alt={title} />
        </div>
      )}
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

function Home() {
  const context = useDocusaurusContext();
  const {siteConfig = {}} = context;
  return (
    <Layout
      title={`ZIO gRPC: Write gRPC services and clients with ZIO`}
      description="ZIO gRPC lets you write purely functional gRPC services in Scala using ZIO.">
      <header className={clsx('hero hero--primary', styles.heroBanner)}>
        <div className="container">
          <h1 className="hero__title">{siteConfig.title}</h1>
          <p className="hero__subtitle">{siteConfig.tagline}</p>
          <div className={styles.buttons}>
            <Link
              className={clsx(
                'button button--outline button--secondary button--lg',
                styles.getStarted,
              )}
              to={useBaseUrl('docs/')}>
              Get Started
            </Link>
          </div>
        </div>
      </header>
      <main>
        {features && features.length > 0 && (
          <section className={styles.features}>
            <div className="container">
              <div className="row">
                {features.map((props, idx) => (
                  <Feature key={idx} {...props} />
                ))}
              </div>
            </div>
          </section>
        )}
      </main>
    </Layout>
  );
}

export default Home;
